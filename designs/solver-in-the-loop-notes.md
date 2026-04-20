# Solver-in-the-loop — current state and next steps

Captures the post-pilot investigation of why byte-array pilots don't yet
get useful solver guidance, and what the real blockers are.

## What works (as of commit `744e1d5` + green `7f2b048`)

- **Tag propagation through `ByteArrayInputStream`.** Galette's
  `SystemMasks.arraycopy` invokes `ArrayTagStore.arraycopyTags` which
  copies the per-element `Tag[]` mirror alongside the byte bulk copy.
  Verified directly: a byte-array tagged via `Symbolicator.symbolic`,
  fed through `bais.read()` into an explicit branch, produces a non-
  empty Z3 model (`b_b0` / `b_b1` constraints round-trip to the solver
  and back).
- **Z3 sort consistency for byte arrays.** `Symbolicator.symbolic(byte[])`
  now tags elements as `BV(32)` to match Green's byte-array range sort.
  `BIT_AND` in `Z3JavaTranslator` now coerces `IntNum` and `ArithExpr`
  on both sides.
- **Graceful degradation.** Knarr-server catches solve failures and
  returns an empty solution so the client socket stays alive and the
  pilot's structural fallback mutator keeps exploring.

## Measurement: proper concolic (4-mutator sweep, post-8db5d65 + concolic driver)

Ran each pilot 10 iterations × 4 mutators on `/tmp/jdk-dual-ga-first`.
The `concolic` mutator implements classic branch negation: maintain a
separate ordered list of recorded predicate constraints, pick a branch
index `i` round-robin, and send `anchors ∧ b1 ∧ ... ∧ b(i-1) ∧ ¬bi`
to the solver for a model. Falls back to structural flip on UNSAT or
empty model.

| Pilot | Mutator | Outcomes | Branches |
|---|---|---:|---:|
| Tar | struct | 3 | 1691 |
| Tar | random | 2 | 1549 |
| Tar | solver | 1 | 1377 |
| Tar | **concolic** | **3** | **1731** |
| Ant | struct | 3 | 256 |
| Ant | random | 3 | 258 |
| Ant | **solver** | **4** | **318** |
| Ant | concolic | 3 | 256 |

**Tar: concolic wins.** The per-iter log shows a successful flip on
iter 0, then UNSAT on every subsequent negated prefix. The one
successful flip is enough to break out of the `ENTRY_READ bucket=0`
plateau that plain solver stayed locked in (1377 branches) — concolic
records 1731, edging past the best baseline (struct's 1691).

**Ant: concolic skips every iter with `reason=no_branches`.** Even
though IF_ICMPXX / LCMP paths in `PathConstraintListener` route to
`_addBranchDet`, the Ant parser's XML reads happen through a chain
(xerces SAX → `DocumentScannerImpl` → char-array ops) where the byte
tags don't survive to the comparison branches. By the time a branch
fires, it's comparing an un-tagged int, so nothing lands in the
branch list. Net: concolic falls back to structural flip every iter
and matches struct exactly (256 / 3). Solver-guided "re-satisfy"
stays the winner on Ant because the full constraint tree (including
anchors and derived equalities) still feeds meaningful model
diversity downstream.

**Takeaways:**

1. Different parsers favor different concolic strategies —
   solver-guided "re-satisfy" wins where outcome categories are
   brittle to seed bytes (Ant's XML); classic branch-negation wins
   where the constraint tree is dominated by a single gate and the
   parser plateaus inside it (Tar's ustar header).
2. The main lever for improving concolic on Ant is **broader branch
   recording** — the classifier that decides "this is a predicate,
   not an anchor" currently misses branches where tags were dropped
   upstream. A next step: trace where byte tags get lost through
   xerces.
3. Even with only 1 successful flip per session, classic concolic
   beats both "never flip" (struct) and "always re-satisfy" (solver)
   on a gate-dominated target like Tar. Worth keeping in the toolbox.

## Measurement: solver-guided vs. structural / random (3-mutator sweep, pre-concolic)

Ran each pilot 10 iterations × 3 mutators on `/tmp/jdk-dual-ga-first`:

| Pilot | Mutator | Outcomes | Branches |
|---|---|---:|---:|
| Tar | struct | 3 | 1692 |
| Tar | random | 2 | 1856 |
| Tar | solver | 1 | 1406 |
| Ant | struct | 3 | 256 |
| Ant | random | 4 | 264 |
| Ant | solver | 4 | 318 |

**Reading:** on Ant the solver behaves as expected — more distinct
branch coverage AND more outcome categories than either baseline. On
Tar the naive "feed the solver's satisfying model back as the next
input" strategy drives the parser *deeper into the same outcome
bucket* (every iter lands in `ENTRY_READ bucket=0` with ~1100 branch
events per iter) rather than *flipping to a new outcome*. Net: tar
coverage sits near the struct baseline, not above it.

**What this tells us:** Ant's XML parser has more distinct parse
outcomes reachable by single-byte mutations (well-formed → `Invalid
byte N` → `Element type "_"` → `Content is not`), so any satisfying
model that differs from the seed tends to land in a new bucket. The
tar header has a narrow validity gate (magic at offset 257, null-
terminated name at offset 0) so most satisfying models re-land in
`bucket=0`. To break through on tar we'd need the mutator to *negate*
a branch condition rather than *re-satisfy* the existing path — a
real concolic step rather than just "re-solve current PC". Parked
for a follow-up session.

## Update: Tar-parser UNSAT resolved (post 62b9f23)

Root cause: `PathConstraintListener.recordIntCmp` had its `GT`/`LT`
operators swapped. When the concrete LCMP/DCMPL/FCMPL outcome was
`v1 < v2`, the listener recorded `GT(l, r)` (i.e. `l > r`) — the
opposite of the symbolic relation. Long-comparing the unsigned tar
checksum (815) against `parseOctal` of the zero-filled checksum field
(0) emitted `LT(sum, 0)` instead of `GT(sum, 0)`, which — combined
with the positive bytewise sum implied by `0xFF & tar_bN ≥ 0` over
every header byte — was unsatisfiable. Same bug existed in the mirror
`recordRealCmp` (double/float paths), fixed identically. Verified via
an unsat-core bisector: with the swap fixed, the raw 1364-constraint
tar parse constraints are SAT and TarZ3Verify3 returns a 1037-entry
model. Both pilots still pass.

## Original investigation notes



The commons-compress tar-header parse produces ~340 path-condition
constraints on a single `getNextEntry()` call. Z3 reports **UNSAT** on
that constraint tree — even though the concrete execution with a valid
ustar seed is a literal witness that the path is satisfiable.

Verified diagnostics:

- `PathUtils.getCurPC().size` grows from 0 → 340 during the parse
  (resetting right before tagging), so constraints are being recorded.
- Server-side, `generateAndAddNewOptions` adds one option, the solver
  is invoked on it, Green returns `sol.sat == false` with
  `sol.data.size() == 0`.
- Result is stable whether the canonizer is enabled (`dedup=true`) or
  bypassed — so the canonizer isn't the source of the inconsistency.
- Adding byte-range bounds (`x in [-128, 127]`) on every tagged
  element doesn't change UNSAT → UNSAT isn't a range-bound issue.
- A much smaller byte-array probe (tag, `bais.read()` twice, two
  explicit branches) is SAT end-to-end → the UNSAT is specific to
  shape/volume of the tar parse's constraints, not to byte-array
  handling in general.

Hypotheses for the remaining wall, in order of suspicion:

1. **Mixed-sort ops elsewhere in Green's translator.** `BIT_AND` had
   a sort-coercion bug; peers like `BV2I`, `I2R`, shift operators, or
   `EXTRACT` around `I2B` may have similar shapes that produce
   silently-wrong Z3 terms. Reproduction path: dump `req.constraints`
   on the server for a tar parse and feed the raw tree through Green +
   Z3 standalone to bisect.
2. **Knarr constraint generation for a specific tar-parser operator
   pattern.** commons-compress's octal-digit validation reads each
   header byte, does `b - '0' & 0xFF`, checks `< 10`. If Knarr emits
   a constraint that uses `0xFF` as a signed-vs-unsigned mask in an
   inconsistent way, the chain goes UNSAT.
3. **`onArrayLoad`'s `cellConst == SELECT(arr, i)` anchor.** For each
   tainted BALOAD the listener anchors the concrete byte value to a
   SELECT. If commons-compress mutates the input buffer mid-parse
   (unlikely with `ByteArrayInputStream`'s own state vs. the shared
   array — its `read()` just walks `pos`), the anchor would pin the
   cell to two different constants at different pos values and UNSAT.
   Worth a second look by logging the `EQ` constraints as they're
   added.

## Concrete next-session work to unblock solver guidance

1. **Dump a real tar constraint tree**: save `PathUtils.getCurPC()
   .constraints` after a tar parse to a file. Feed it directly into
   Green's Z3 translator in a standalone JVM and have Z3 report the
   unsat core. The core names the specific subset of constraints that
   are collectively inconsistent — which localises the bug to a
   specific Knarr operator path.
2. **Widen Z3JavaTranslator coercions** using the pattern already
   applied to `BIT_AND` to every binary BV operator that casts both
   operands. `BIT_OR`, `BIT_XOR`, `SHL`, `SHR`, `USHR`, `BVSDiv`,
   `BVSRem`, etc. each need the same `IntNum/ArithExpr → BV` wrap on
   both sides.
3. **Consider disabling `concreteCellAsConstant` anchoring** as a
   quick sanity check: if UNSAT resolves when anchors are dropped,
   the bug is in the anchor path; if it persists, it's in branch
   constraint generation.

Until the UNSAT lands, the pilot targets rely on structural (loop-
counter-derived) mutation and the solver's empty responses. The
infrastructure — Symbolicator → PathConstraintListener → Green server
→ Z3 round-trip — works correctly on simpler constraint shapes, which
was the load-bearing claim of the pilots.
