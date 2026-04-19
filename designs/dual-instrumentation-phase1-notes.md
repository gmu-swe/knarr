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

## Phase 2 — remaining work

The blockers are infrastructure, not bytecode correctness:

1. **jlink-level composition.** Replicate Galette's jlink plugin layer
   (`GaletteJLinkPlugin`, `PackJLinkPlugin`, `Packer`,
   `ResourcePoolPacker`, `JLinkInvoker`, `JLinkRegistrationAgent`) so
   that a single jlink invocation applies `CompositeTransformer.transform`
   to every resource. CROCHET already ports the same template — the bridge
   needs to generate the ONE plugin that calls both transformers and
   the ONE pack step that merges both runtime surfaces into `java.base`.
   Expect ~300-500 LOC cribbed from the two existing instrument modules.

2. **Runtime agent attachment.** The final JDK image must boot with
   both `-javaagent:galette-agent.jar` and
   `-javaagent:crochet-agent.jar` (order matters: Galette first,
   CROCHET second, so CROCHET's transformer sees Galette-instrumented
   user-class bytes at load time — symmetric with the jlink order). The
   `knarr-concolic` module's pom's `integration-tests` profile should
   set both.

3. **E2E concolic test.** With the dual JDK in hand,
   `knarr-concolic/src/test/java/.../integration/ConcolicLoopITCase.java`
   can:
    - Install a `PathConstraintListener`.
    - `checkpointAll()`.
    - In a loop: tag input → run target → `dumpConstraints` → mutate →
      `rollbackAll(cp)`.
    - Assert multiple distinct paths get explored within a single JVM.

   The loop logic is already written in
   `knarr-concolic/src/main/java/.../ConcolicDriver.java`; only the
   harness + JDK setup is missing.

## Estimated scope

- Phase 2.1 (jlink plugin + pack): ~1-2 days of focused work.
- Phase 2.2 (dual-image smoke test: `java -version` on the combined
  image): half a day.
- Phase 2.3 (E2E concolic test that proves the loop): half a day once
  2.1 + 2.2 land.

Total: ~2-3 days to the first CROCHET-driven concolic test that flips
at least one branch in a single JVM.

## Why we stopped here

Verifying bytecode composition answered the architectural risk question.
Finishing the jlink layer is straightforward but lengthy mechanical
porting from both existing instrumenters, and delivering it inside this
session would have been half-done work. Better to land the probe (and
thus unblock phase 2) with a green test suite than to submit a
half-ported plugin that fails in novel ways.
