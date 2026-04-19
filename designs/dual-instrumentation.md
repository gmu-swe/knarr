# Dual-instrumented JDK (Galette + CROCHET)

## Goal

Produce a single JDK image whose classes are instrumented by BOTH:

- **Galette** (for tag/frame propagation — what Knarr needs to record
  path constraints), and
- **CROCHET** (for checkpoint/rollback — what the concolic driver needs
  to re-run a target without a JVM restart).

Running the driver on this image lets Knarr observe branches while CROCHET
snapshots the pre-execution heap. After each run, `rollbackAll()` restores
state and the driver re-executes with a mutated input — all within one
JVM.

## Why it is hard

Both projects share the same infrastructure pattern (a `java.lang.instrument`
ClassFileTransformer plus a jlink plugin that applies it over every
`.class` in a stock JDK image). They don't interoperate out of the box
because:

1. **jlink cannot re-instrument a jlink'd image.** The output of `jlink` has
   no `jmods/` directory, so a second jlink over the first fails with
   `--module-path is not specified and this runtime image does not contain
   jmods directory`. Chaining `jlink → jlink` is impossible.
2. **Each transformer is self-exclusive.** Galette stamps
   `@GaletteInstrumented` and skips anything already annotated; CROCHET
   does the same with `@CrochetInstrumented`. Without coordination they
   each skip only their OWN artifacts, not the other's.
3. **Shadow methods are the instrumentation surface.** Galette adds
   `methodName(…, TagFrame)` shadow methods; CROCHET adds `$$crochet*`
   helpers and `CRIJInstrumented` interfaces. Each transformer's bytecode
   analysis assumes a pristine input — a Galette-instrumented method with
   a `TagFrame` parameter is not something CROCHET's field-access wrapper
   is prepared to see.

## Options considered

### A. Chain-in-one-jlink-invocation

jlink supports multiple `--instrument=...` plugins in a single invocation.
Write a custom jlink driver that issues:

```
jlink \
    --instrument=x:type=Galette... \
    --instrument=x:type=Crochet... \
    --pack=... \
    --output=/tmp/jdk-dual
```

Both agents can register their `Plugin`s in one JVM (each runs a
`-J-javaagent:` with its own jar). jlink invokes them in declaration
order per resource.

**Blockers:**
- Each transformer is stateful; chaining two `Plugin.transform(bytes)`
  calls means the second sees Galette-annotated classes. CROCHET's
  `@CrochetInstrumented` check would pass (unset), but its
  `FieldAccessWrapper` / `ArrayAccessWrapper` would then rewrite methods
  that already have `TagFrame` plumbing. Unknown whether this is safe.
- Each plugin currently hard-codes its own `--pack` step. Running two
  packs in one jlink would try to pack both agents' runtimes into
  `java.base`. Needs a single combined pack.

### B. Composite transformer module

Write a small new module `crochet-galette-bridge` that provides a SINGLE
jlink plugin, which internally applies
`CrochetTransformer.transform(GaletteTransformer.transform(bytes))` per
class. Runtime is a merged agent jar whose `premain` installs both
transformers' class-file callbacks, in order.

**Advantages:**
- One jlink invocation; one pack step; one agent jar.
- Explicit control over ordering (Galette first so CROCHET's
  transformers see instrumented methods with their shadow surfaces
  already present).

**Costs:**
- New module (~400 LOC bridging the plugin APIs).
- Dependency on both Galette and CROCHET internals; breaks if either
  rev's internal APIs.
- Neither project's `Transformer#transform` is currently designed to be
  callable from an external driver; both assume they're run from their
  own jlink plugin and read system properties / static state. The bridge
  may need to fork a jlink-equivalent loop rather than calling
  `transform(bytes)` directly.

### C. Two-pass image build via an in-place rewrite

Run Galette's jlink first to produce `/tmp/jdk-galette`. Then write a
small tool that walks `/tmp/jdk-galette/lib/modules` (the jlink'd module
image), extracts class bytes, applies CROCHET's transformer
`CrochetTransformer.transform(bytes)` directly, and writes the bytes back.
Relies on the fact that the jlink'd module file is a known format the
JDK's `jrt` filesystem can read — but NOT trivially append to.

**Blockers:**
- `lib/modules` is a sealed jimage format; writing back requires the
  internal `jdk.internal.jimage` API or reconstructing the file.
- Pack step for CROCHET runtime is separate — its runtime classes need
  to live on the bootclasspath; without a pack, they're loaded by the
  agent, which may be too late for classes CROCHET wants to see at
  system-class-loading time.

### D. Accept separate JVMs

The driver JVM uses CROCHET only; a child JVM handles Knarr's
instrumented execution per iteration. The driver parks between runs,
CROCHET is irrelevant for the child.

**Trade-off:**
- Abandons CROCHET's whole-point performance win (each iteration pays
  JVM-start cost).
- But is the ONLY option guaranteed to work without composite-transform
  engineering.

## Recommended path

**Phase 1 (this session):** option D. Package a skeleton
{@code ConcolicDriver} in `knarr-concolic/` that calls the CROCHET API;
document the dual-instrumentation design here; set up CI that builds all
three projects and runs their unit tests independently.

**Phase 2 (a follow-up):** option B. Build the composite transformer as
a new module `crochet-galette-bridge`. Validate incrementally:

1. Run `CrochetTransformer.transform` in a test over a Galette-instrumented
   class byte-string and observe what the output looks like. If CROCHET's
   analyzers crash on shadow methods, fix them there (or skip `$$GALETTE_*`
   / shadow methods in CROCHET's visitors).
2. Once standalone transformer chaining works, wrap it as a jlink plugin.
3. Confirm the dual-image boots (`java -version`).
4. Confirm a trivial test: tag an int, checkpoint, increment, rollback,
   tag matches. If that works, the full concolic driver gates open.

## Gating criteria for "CROCHET E2E concolic test suite"

The suite lives in `knarr-concolic/src/test/java/.../integration/` and
runs only under `-Pconcolic-e2e` (or equivalent). The profile is active
only when both `-Dcrochet.dualJdk` and `-De2e.serverJar` system
properties point to a validated dual-instrumented JDK and a running
knarr-server respectively. Without those properties the tests auto-skip.

Each E2E test has the shape:

```java
ConcolicDriver<Integer> d = new ConcolicDriver<>(
    input -> {
        int x = Symbolicator.symbolic("x", input);
        if (x > 100) throw new AssertionError("found it");
    },
    (prev, soln) -> Integer.parseInt(soln.get(0).getValue().toString()),
    20);
d.run(5);
// assert that at least one iteration drove x above 100
```

i.e., prove that CROCHET rollback + Knarr re-tagging + Z3 solution together
close the concolic loop.
