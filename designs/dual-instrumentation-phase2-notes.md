# Dual instrumentation — phase-2 findings (Galette-first composition)

Companion to [`dual-instrumentation.md`](./dual-instrumentation.md) and
[`dual-instrumentation-phase1-notes.md`](./dual-instrumentation-phase1-notes.md).
This file captures what phase 2 (Galette-first dual JDK image + boot +
agent-time loop) actually achieves and where it hits the wall.

## Strategy chosen

Option **(c) — variant**, from [`dual-instrumentation.md`](./dual-instrumentation.md).
The phase-2 driver
[`crochet-galette-bridge/.../DualImageInstrumenter.java`](../crochet-galette-bridge/src/main/java/edu/gmu/swe/knarr/bridge/DualImageInstrumenter.java)
takes a Galette-jlinked JDK as input, extracts its `lib/modules`
jimage to an exploded directory, runs `CrochetTransformer.transform`
on every `.class` file, packs the CROCHET runtime (filtered through
`shouldPackCrochet`) into the exploded `java.base`, patches
`java.base/module-info.class` to declare the new packages and exports,
and re-runs `jlink` to emit `/tmp/jdk-dual-ga-first/`.

Why (c) over (a) or (b):

- (a) requires reading the jimage format with `BasicImageReader`
  internals and there is no public `jimage create` to repack;
  multi-step `jimage extract` + `jlink --module-path=<exploded>`
  achieves the same effect with public tooling.
- (b) ("two `--instrument=` plugins in one jlink") would require
  hosting both `JLinkRegistrationAgent`s in the same jlink JVM and
  merging the two `--pack=` steps into a combined plug-in that bakes
  Galette + CROCHET runtimes into `java.base` together. The `--pack=`
  collision is a real blocker even before the dual-classloader probe,
  and the task explicitly flags it.

## What works

1. **Build.** `DualImageInstrumenter` runs end-to-end in ~40 s and
   produces `/tmp/jdk-dual-ga-first/` with both Galette's runtime
   (already packed by Galette's first jlink) and CROCHET's runtime
   (packed by phase-2) present in `java.base`. Class count: 26 777
   classes processed by `CrochetTransformer`, ~300 hit
   `MethodTooLargeException` (mostly `LocaleNames_*` /
   `TimeZoneNames_*` resource bundles already at the JVM size limit
   after Galette's pass) and keep their pre-CROCHET bytes.

2. **Boot.** `/tmp/jdk-dual-ga-first/bin/java -version` succeeds.

3. **HelloWorld with both `-javaagent`s.** Galette + CROCHET agents
   load (in either order); a trivial main runs.

## What hits the wall

The CROCHET demo `01-basic` (and the new `ConcolicLoopITCase`) crashes
with `AbstractMethodError`:

    Receiver class Counter does not define or inherit an implementation
    of the resolved method 'abstract void $$crochetCheckpoint(int,
    edu.neu.ccs.prl.galette.internal.runtime.TagFrame)' of interface
    net.jonbell.crochet.runtime.CRIJInstrumented.
        at java.base@17.0.18/net.jonbell.crochet.runtime
            .CheckpointRollbackAgent.checkpoint(CheckpointRollbackAgent.java:87)

Root cause is the agent-time pipeline ordering. With `-javaagent:galette
-javaagent:crochet`, on a freshly-loaded user class:

1. Galette's transformer fires first. The class has no `$$crochet*`
   surface yet, so Galette adds nothing for those methods.
2. CROCHET's transformer fires second. It adds
   `$$crochetCheckpoint(int)`, `$$crochetRollback(int)`, etc.
3. Galette is **not invoked again**. The `$$crochet*` methods never
   acquire the `(TagFrame)` shadow that the (Galette-pre-transformed)
   CROCHET runtime expects to dispatch to.

Reversing the agent order (`-javaagent:crochet -javaagent:galette`)
hits a different wall: CROCHET's `TransformerWrapper` invokes
`new ClassReader(byte[])` from inside `CrochetTransformer.transform`,
but the packed CROCHET runtime's transformer was Galette-pre-shadowed
to call `new ClassReader(byte[], TagFrame)`. The shaded
`net.jonbell.crochet.agent.shaded.asm.ClassReader` doesn't have that
constructor — `NoSuchMethodError` on every class load. (This is why
the bridge's pre-Galette-transform of CROCHET runtime in
`packCrochetRuntime` does include the shaded ASM package: skipping it
splits the world the same way and produces the same crash on a
different surface.)

Key bytecode/runtime ordering observation (dumped via
`-Dcrochet.dumpClasses=true`):

    public class Counter implements TaggedObject, CRIJInstrumented {
      // Galette added these (saw the original methods first):
      public Counter(int, String, TagFrame);
      public Class getClass(TagFrame);
      // ... full Object surface in TagFrame'd form ...
      // CROCHET added these (after Galette, no second Galette pass):
      public void $$crochetCheckpoint(int);
      public void $$crochetRollback(int);
      public void $$crochetCopyFieldsTo(Object);
      public void $$crochetCopyFieldsFrom(Object);
    }

The `(int, TagFrame)` versions of the `$$crochet*` methods are
missing — Galette would have generated them if it had run again after
CROCHET.

## Other phase-2 surprises worth remembering

- **`generate-jli-classes` jlink plugin must be disabled** on the
  second jlink: Galette's first jlink already injected pre-generated
  `BoundMethodHandle$Species_*` classes; the default plugin tries to
  add them again and fails with "Resource ... already present".
- **`system-modules` jlink plugin** regenerates
  `jdk/internal/module/SystemModules*.class` based on the input
  module-info. We can't simply disable it (the new CROCHET packages
  in `java.base` need to be declared in those generated classes); but
  the regenerated classes lose Galette's `(TagFrame)` shadow
  signatures. Phase-2 fix: run jlink twice — first to let
  `system-modules` emit fresh classes, then re-run Galette's
  transformer on those fresh classes, drop them back into the
  exploded module dir, and run jlink again with `--disable-plugin
  system-modules` to preserve the now-Galette-instrumented copies.
- **`ModuleHashes` attribute** on `java.base/module-info.class`
  records hashes of tied modules and rejects any image where the
  modules' bytes have been modified. The bridge strips it before
  re-jlinking; jlink fills its own hash if it wants one.
- **Galette's exported java.base packages list** in
  `java.base/module-info.class` (as written by Galette's
  `ResourcePoolPacker`) contains 246 entries by `javap -v` count, of
  which ~66 are actually MODULE NAMES (e.g. `java.desktop`,
  `jdk.internal.jvmstat`) that ASM correctly drops on read-back.
  The 180-entry post-ASM `cn.module.packages` list is the legitimate
  package count and is what the bridge writes back.
- **Galette runtime classes** in
  `java.base/edu/neu/ccs/prl/galette/...` MUST be skipped by
  CROCHET's transformer. Without the skip,
  `IndirectTagFrameStore.<clinit>` chains into
  `CheckpointRollbackAgent.<clinit>` chains into
  `FastProxySupport.<clinit>` chains into
  `Throwable.<clinit>` and SIGSEGVs because Throwable is being
  initialized inside the JVM's exception-construction path before
  CROCHET runtime is allocated.

## Wall summary

Galette-first composition produces a JDK image that boots and runs
plain Java code under both agents, but cannot run any program that
exercises CROCHET's checkpoint/rollback API end-to-end. The
`AbstractMethodError` on `$$crochetCheckpoint(int, TagFrame)` is the
canonical signature of the problem.

To unblock, the agent-time pipeline must apply BOTH transformers to
each class load, in a single pass, so the CROCHET-added `$$crochet*`
methods see the Galette pass that produces their `(TagFrame)`
shadows. The bridge's
[`CompositeTransformer`](../crochet-galette-bridge/src/main/java/edu/gmu/swe/knarr/bridge/CompositeTransformer.java)
already does this for the bytecode probe; phase 2.5 needs a single
agent jar that registers the composite as its
`ClassFileTransformer`, replacing the two
`-javaagent:galette,crochet` invocations.

## Status of phase-2 deliverables

| Deliverable | State |
| --- | --- |
| `DualImageInstrumenter` (option C) | Lands in `crochet-galette-bridge/`. |
| `/tmp/jdk-dual-ga-first/` builds | Yes (~40 s on the host JDK 17). |
| `bin/java -version` boots | Yes. |
| HelloWorld + both agents runs | Yes. |
| CROCHET demo 01-basic runs | **No** — `AbstractMethodError`. |
| `ConcolicLoopITCase` reaches throw | **No** — child crashes on the same wall. The test asserts skip + records the failure mode so the wall is captured in CI. |
| `concolic-e2e` Maven profile | Lands in `knarr-concolic/pom.xml`, activated by `-Dcrochet.dualJdk`. |
