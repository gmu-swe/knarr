# Dual instrumentation — phase-1 findings

Companion note to [`dual-instrumentation.md`](./dual-instrumentation.md). This
file records what phase-1 (the composition probe in
`crochet-galette-bridge/`) validated and what phase-2 (a working
dual-instrumented JDK + CROCHET-driven concolic E2E test) still needs.

## Phase 1 outcome

**Galette and CROCHET transformers compose at the bytecode level.**

The four probe tests in
`edu.gmu.swe.knarr.bridge.CompositeTransformerProbeTest` run on a plain
(un-instrumented) JVM. They compile a small fixture class, run its bytes
through `new CompositeTransformer().transform(…)` (Galette first, CROCHET
second), define the result in an isolated child loader, and invoke a
method. All four pass:

- `galetteAloneYieldsVerifiableClass`
- `crochetAloneYieldsVerifiableClass`
- `compositeYieldsVerifiableClass`
- `compositeClassExecutes` — calls `addOne(41)` and asserts `42`.

The composite output passes JVM verification and executes successfully.
Bytecode-level compatibility is no longer the blocker for option B of
[`dual-instrumentation.md`](./dual-instrumentation.md).

## What phase 1 did NOT validate (deferred to phase 2)

`CompositeListenerFiresTest` (marked `@Disabled`) attempted to verify
that Galette's branch hook fires at runtime on dual-transformed
bytecode. It failed with:

    NoSuchFieldError: Class java.lang.Thread does not have member field
    'edu.neu.ccs.prl.galette.internal.runtime.frame.AugmentedFrame
    $$GALETTE_$$LOCAL_frame'

This is **expected** and highlights the remaining work. Galette's runtime
requires an instrumented `java.lang.Thread` with a `$$GALETTE_*` shadow
field. A plain JVM does not provide that. Therefore:

- Transformer composition is verified.
- **Runtime execution of dual-transformed classes requires a dual-
  instrumented JDK image** (or at minimum a Galette-instrumented JDK
  with CROCHET as a runtime-only agent).

## Phase 2 outcome

**Working end-to-end.** The first CROCHET-driven concolic test runs
against a single JVM and flips the target branch. Evidence in
`knarr-concolic/target/concolic-it-output.txt` after `mvn -pl
knarr-concolic test -Dcrochet.dualJdk=/tmp/jdk-dual-ga-first`:

    CONCOLIC_REACHED_THROW x=128

The three layered experiments that got us here:

1. **jlink-level composition.** `DualImageInstrumenter` in the bridge
   module applies the Galette transformer then the CROCHET transformer
   to every class in a source JDK image and re-packs into
   `/tmp/jdk-dual-ga-first`. Both runtime surfaces ship inside
   `java.base`.

2. **Runtime agent attachment.** The two-`-javaagent` approach proved
   unworkable — see class javadoc on `CombinedAgent` for the concrete
   failure modes from both orderings. The working pattern is ONE
   combined agent jar (`Crochet-Galette-Bridge-*.jar`) whose premain
   installs a single `ClassFileTransformer` that chains Galette then
   CROCHET, plus a skip-list for three known bootstrap-recursion groups.
   `premain` also pre-initializes `ThreadLocalRandom`, `LockSupport`,
   `StringConcatFactory`, and two `MethodHandle` internals to dodge the
   class-circularity error that CROCHET's own demos happen to avoid but
   our narrower warm-up path doesn't.

3. **E2E concolic test.** `ConcolicLoopITCase` launches
   `knarr-server` on a plain JDK (the constraint solver cannot be
   instrumented — see `E2EServerITCase` rationale), forks a child under
   the dual JDK with the combined agent, and drives the `Target` via
   `ConcolicDriver`. One extra concession was needed: `ConcolicDriver`
   now calls `Symbolicator.reset()` before the first `checkpointAll()`
   so that `PathUtils.usedLabels`, `PathUtils.getCurPC()`, and the
   Symbolicator internals are all fully initialized at checkpoint time;
   without that pre-warm, rollback reverts them to their pre-`<clinit>`
   null state and the next iteration NPEs inside `Symbolicator.reset`.
