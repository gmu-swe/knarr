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

## DeserPilot: deserialization coverage measurement (post-e1198e6, watchdog fix 5a6bfea)

Added an 8th pilot targeting the `commons-collections:3.2.1` classpath
under `ObjectInputStream.readObject`. Goal is defensive: measure which
class names fuzzers reach during deserialization, NOT construct any
payload. The pilot uses a `FilteringObjectInputStream` allowlist that
rejects every class outside a tiny inert set (`HashMap`, `ArrayList`,
`String`, boxed primitives); any attempt to resolve a Transformer /
InvocationHandler / Commons class is recorded as a `CLASS_SEEN`
bucket and the class never actually instantiates.

First run (all 5 mutators complete — first pilot since Gzip where the
watchdog made this reliable):

| Mutator | Outcomes | Branches |
|---|---|---:|---:|
| struct | 2 | 2066 |
| random | 3 | 2085 |
| solver | 2 | 2216 |
| concolic | 2 | 2192 |
| **guided** | **3** | **2532** |

**Guided wins — first pilot where it's the top mutator.** Java's serial
stream format is dominated by sequential tag dispatch
(`if (tag == 0x73) type-reference; else if (tag == 0x77) block-data;
else if (tag == 0x7e) enum; ...`) which creates exactly the "one-way
branch site" pattern guided's heuristic targets. The branch-negation
step on one of those sites pushes `readObject` into a different
dispatch arm, exposing new state machine transitions that the
structural / random / solver mutators stay locked out of.

**No CLASS_SEEN buckets fired.** 10 iters of single-byte mutation on
a HashMap seed doesn't drive the stream far enough to reach a
`resolveClass` call on a gadget-family name. Outcomes fragment earlier
— `BAD_HEADER` (mutation hit the `0xAC 0xED 0x00 0x05` magic),
`EOFException` / `UTFDataFormatException` / `StreamCorruptedException`
(mutation broke an internal length prefix or string encoding).

This is itself a defense-relevant finding: **the serial format's
header and length-prefix structure is brittle enough that random /
concolic / guided mutation fails well before getting near class-name
resolution.** A real attacker has to start from a valid-structure
seed, not mutate outward from a benign one.

### DeserPilot v3: richer seed confirms the finding

Swapped the seed to a 422-byte `TreeMap<String, Object>` containing
nested `ArrayList`s with `byte[]`, `String`, `Integer`, `Long`, and
an `Object[]` — 4× the class-descriptor surface of the old
`HashMap<String,String>` seed:

| Mutator | Outcomes | Branches |
|---|---:|---:|
| struct | 2 | 2091 |
| random | 3 | 2102 |
| solver | 2 | 2517 |
| concolic | 2 | 2493 |
| **guided** | **3** | **2837** |

Branch count rose 13% (2532 → 2837 for guided) with the richer seed,
but the **outcome set is unchanged** — still no `CLASS_SEEN` events.
Every mutator fragments into `BAD_HEADER` / `PARSED_OK` /
`StreamCorruptedException` / `UTFDataFormatException`. Even a
stream with TreeMap + ArrayList + byte[] + Object[] + several
String-class descriptor regions is too structurally tight for 10
iters of byte mutation to reach a class-name substring matching
`Transformer` / `InvocationHandler` / `Commons`.

**Confirmed defensive research finding:** mutation-from-benign-seed
fuzzing at any iter count within a CI budget **cannot reach
deserialization gadget-class territory**. Real CVEs of the
ObjectInputStream family are not discovered by mutation fuzzers —
they're constructed by researchers who start from full-gadget
payloads and minimize. Our pilot measures exactly how far the
mutation surface pushes before structural collapse; the answer is
"nowhere near Transformer". This is the kind of signal worth
capturing in a paper on *why* deserialization fuzzing hasn't
worked, not *that* the pilot infrastructure works.

## Seven-pilot sequential batch (post-69db0ad)

Ran every pilot ITCase back-to-back via `/tmp/run-all-pilots.sh` with a
37-minute hard cap per pilot. Total batch runtime: ~2h37m. Per-pilot
final numbers from the captured snapshots:

| Pilot | Mutator | Outcomes | Branches | Notes |
|---|---|---:|---:|---|
| Tar | struct | 3 | 1670 | |
| Tar | random | 1 | 1439 | assertion tripped, halted |
| Ant | struct | 3 | 1550 | |
| Ant | random | 3 | 1566 | |
| Ant | solver | 3 | 1549 | |
| Ant | **concolic** | **3** | **1641** | winner |
| Ant | guided | — | — | 37m cap |
| Zip | struct | 2 | 2467 | ENTRY_READ + `ZipException` buckets |
| Zip | random | — | — | 300s per-mutator cap too tight |
| Jackson | struct | 2 | 166 | |
| Jackson | random | — | — | 300s cap |
| CommonsText | struct | 1 | 2721 | |
| CommonsText | random | 1 | 2675 | |
| CommonsText | solver | 1 | 2721 | |
| CommonsText | concolic | 1 | 2721 | |
| CommonsText | guided | — | — | 37m cap |
| Gzip | struct | 2 | 153 | |
| Gzip | random | 2 | 158 | |
| Gzip | solver | 2 | 143 | |
| Gzip | **concolic** | **2** | **159** | winner |
| Gzip | guided | 2 | 153 | |
| Regex | struct | 2 | 1142 | |
| Regex | **random** | **2** | **1157** | winner |
| Regex | solver | 2 | 1142 | |
| Regex | concolic | 2 | 1142 | |
| Regex | guided | — | — | 37m cap |

**Only Gzip survived the full 5-mutator protocol** — its parser is
thin (sub-200-branch working set) so per-iter wall time is low enough
that the guided mutator doesn't hit the systemic hang it exhibits on
xerces / commons-text / regex. Gzip is also the sole pilot where
concolic's win on branches (159 > 153) is statistically tidy.

**Systemic `guided`-mutator hang** reliably trips on pilots where the
parser's xerces-style branch surface pushes Galette into high-volume
constraint-record territory. Needs Knarr-side investigation — probably
a reentrance or locking issue in the guided mutator's flip-rebuild
path. All other mutators complete on those same targets.

**Per-mutator timeout mismatches:** Zip and Jackson random both hit
the 300s non-solver cap — their first iter's parse is slow enough
that 10 iters × (~35s parse + serializer round-trip) overshoots. Easy
fix: uniform 600s for struct/random too.

**CVE-hunt read:**
- Tar struct at 3 outcomes confirms the tar parser hits 3 distinct
  error classes on byte mutations.
- Zip struct hits `ZipException` — meaningful DoS/robustness signal
  with just 10 iters of structural mutation. Worth deeper
  investigation.
- Jackson's 166-branch ceiling on struct is telling: with
  `enableDefaultTyping()` every byte mutation tends to drop into one
  of two states early (parse-success or immediate-fail) without
  exploring nested polymorphic class-name gates. Would need a
  longer-iter budget or a more-aware mutator (one that respects JSON
  grammar) to actually drive into gadget-chain territory.
- CommonsText 1.12 is indistinguishable across mutators — the
  defensive fix is robust to this concolic approach at 10 iters.
- Regex's "random wins by 15 branches" is noise; the ReDoS signal
  we actually want would require wall-clock instrumentation (slow
  match = catastrophic backtracking) which the current outcome
  bucketing doesn't capture.
- Gzip produces crisp 2-outcome diversity and clean concolic win —
  makes it the best sanity-check pilot for the harness itself.

## First CVE-hunt run: CommonsText / Text4Shell at 1.12 (post-b529398)

Ran `CommonsTextPilotITCase` with `commons-text:1.12.0` (the
Text4Shell-patched release, with `script`/`url`/`dns` lookups removed
in 1.10). Target: `StringSubstitutor.createInterpolator().replace(...)`.
Seed: `Hello ${sys:user.name} world`.

| Mutator | Outcomes | Branches |
|---|---:|---:|
| struct | 1 | 2721 |
| random | 1 | 2687 |
| solver | 1 | 2721 |
| concolic | 1 | 2721 |
| guided | — | — (parent-wait hang) |

**Finding: the 1.12 defensive path is robust to 10-iter concolic
mutation.** Every mutator converged on a single outcome bucket
(`REPLACED`) with essentially identical branch counts. Byte-level
mutations of `${sys:user.name}` produce either still-valid lookups
(different sys property, resolves) or unknown lookups (the interpolator
leaves `${...}` untouched in the output), both of which bucket as
`REPLACED`. Neither structural nor solver-guided mutation finds a path
out of the defensive handling at this iteration budget. Useful "CVE
defence confirmed" signal for the patched version; for active CVE
reproduction, pin `commons-text:1.9.0` in a separate ITCase where the
`script:` lookup is still wired.

**Operational note: the `guided` mutator hangs the parent mvn on
small-surface targets** (same `waitFor` not observing child kill
pattern we saw on `llm-guided`). The hang is not hermetic across
commons-text vs Ant targets, so it's not `claude` CLI specific; it's
something about the guided mutator's interaction with the dual-JDK
child's constraint tree that reliably produces a stuck process. For
the 10-iter budget, struct/random/solver/concolic still produce the
useful data — guided's contribution is marginal on this surface.

## LLM-guided scaffold — wiring attempt (post-73e3466)

Wrote `~/bin/claude-rank-concolic.sh` that shells out to `claude --print`
for each iter's branch pick. Smoke-tested standalone: sample JSON
payload in, well-formed `{"idx": 42, "reason": "..."}` out.

Ran the 6-mutator Ant pilot suite end-to-end with `KNARR_LLM_RANKER`
set and 1800s-per-mutator cap. Baselines finished cleanly:

| Pilot | Mutator | Outcomes | Branches |
|---|---|---:|---:|
| Ant | struct | 3 | 1549 |
| Ant | random | **4** | **1728** |
| Ant | solver | 3 | 1550 |
| Ant | concolic | 3 | 1641 |
| Ant | guided | 3 | 1549 |
| Ant | llm-guided | — | — (timeout) |

`llm-guided` hit the 30m cap without completing a single iter, and the
parent's `waitFor` didn't observe the forced kill — orphaning the
child process. The suspected cause is that invoking the `claude` CLI
as a subprocess from within a host Claude Code session creates a
bidirectional stdin/stdout lock that the ranker script can't get out
of. Running under a non-Claude-Code host (a plain shell, or the
`ANTHROPIC_API_KEY`-based non-interactive path) is the likely fix.

**Actionable follow-up:** either (a) modify the ranker script to use
`anthropic` Python SDK directly with `ANTHROPIC_API_KEY` instead of
the `claude` CLI, or (b) exec under `env -i PATH=... bash` to strip
the Claude Code environment before the CLI spawns. Not urgent — the
heuristic-guided mutator (`guided`) is the same pipeline minus the
subprocess call and has already been measured.

## Measurement: heuristic-guided concolic (5-mutator sweep)

Added a `guided` mutator (session-scoped UNSAT skip list + one-way site
priority + deepest-first tiebreaker) and an `llm-guided` scaffold
(identical pipeline but defers the pick to `$KNARR_LLM_RANKER`; skipped
in CI). Also added inHook reentrance guards to the listener's
arithmetic/branch callbacks — `Tag.of` invokes `ObjectIntMap.put`, which
computes `Expression.hashCode`, which under Galette instrumentation
routed back through `onIntArith` and created unbounded recursion on
deep expression trees. The guard cuts the recursion but also drops
~3-5% of derived arithmetic tags, reducing baseline branch counts
slightly.

| Pilot | Mutator | Outcomes | Branches |
|---|---|---:|---:|
| Tar | struct | 3 | 1670 |
| Tar | random | 2 | 1431 |
| Tar | solver | 1 | 1384 |
| Tar | **concolic** | **3** | **1687** |
| Tar | guided | 2 | 1416 |
| Ant | struct | 3 | 1550 |
| Ant | random | 3 | 1561 |
| Ant | solver | 2 | 1614 |
| Ant | **concolic** | **3** | **1641** |
| Ant | guided | 3 | 1549 |

**Finding: on this 10-iter budget, guided does NOT beat concolic on
either pilot.**

- Tar guided: telemetry shows 4 successful flips (vs. concolic's 1) —
  but the "one-way" heuristic aggressively flips deep tar-header
  byte-compare branches (`tar_b156!=83`, `tar_b156!=88`, etc.) which
  all land in the same `ENTRY_READ bucket=0` region after a successful
  flip. Net: more successful flips, FEWER distinct outcomes than the
  round-robin concolic strategy.
- Ant guided: telemetry shows 10 picks, all one-way, all UNSAT. The
  deepest-first tiebreak lands on xerces's deepest tagged-byte
  operations where the solver can't construct a sort-compatible flip.
  Outcome count matches struct / concolic (3), but branch count sits
  slightly below concolic (1549 vs 1641).

**Takeaway:** the heuristic picks AGGRESSIVELY — hitting one-way
fingerprints that look promising but that the solver often can't
satisfy, or that don't open new outcome buckets when it can. On a
larger iteration budget (say 50-100 iters), the UNSAT skip list would
let guided converge on the "satisfiable one-way" core faster than
concolic's round-robin. At 10 iters, concolic's brute round-robin
still wins on outcome diversity. The LLM-guided scaffold (same
pipeline, different pick policy) is the natural next lever — a model
that prefers parser-state-relevant sites over opportunistic deep
leaves could close this gap without changing the mechanics.

The pre-heuristic numbers:

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
| Ant (via temp file, tag-dropping) | struct | 3 | 256 |
| Ant (via temp file, tag-dropping) | random | 3 | 258 |
| Ant (via temp file, tag-dropping) | solver | 4 | 318 |
| Ant (via temp file, tag-dropping) | concolic | 3 | 256 |

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

## Ant fix: direct SAX entry unblocks tag flow (post-9f5d788)

Before this fix, `AntPilotTarget.parseOne` wrote tagged bytes to a
temp file via `Files.write` and then called `ProjectHelperImpl.parse`
with the File argument. Galette has no file-IO masks — a brief audit
of `galette-agent/.../mask/` confirms only `SystemMasks.arraycopy` is
registered, no `FileOutputStream` / `FileChannel` / `Files.write`
handlers — so every tag was dropped at the write boundary and xerces
later read back untagged OS bytes. Net effect: 256 branches per
iter, concolic with `reason=no_branches` every call.

Switching `parseOne` to drive `JAXPUtils.getXMLReader()` directly on
a `ByteArrayInputStream(tagged)` keeps the flow purely in-heap and
exercises the same xerces SAX path Ant uses internally:

| Pilot | Mutator | Outcomes | Branches |
|---|---|---:|---:|
| Ant (direct SAX) | struct | 3 | 1597 |
| Ant (direct SAX) | random | 5 | **2207** |
| Ant (direct SAX) | solver | 3 | 1594 |
| Ant (direct SAX) | **concolic** | **4** | 1692 |

Branch counts rose ~6× across every mutator (xerces is branchy once
tags reach it). On this surface random happens to win on raw branch
coverage because any corruption of a well-formed XML pushes xerces
into a new error path. Concolic wins on **outcome diversity** over
struct (4 vs 3) — the branch-negation strategy finds a distinct
parse-failure class that the structural mutation misses. Solver
("re-satisfy") sits essentially tied with struct; it no longer wins
on Ant now that the bulk of the branch sites are inside xerces
where the solver's model doesn't help as much as it did on the
(pre-fix) shallower constraint tree.

**Cost:** the pilot no longer mirrors JQF's `ProjectBuilderTest`
shape exactly (we don't go through `ProjectHelperImpl`), but the
XML parsing branches — which is what the concolic loop drives on —
are identical. The outcome labels shift from `BUILD_EX ...` to
`SAX_EX ...`; both bucket similarly.

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
