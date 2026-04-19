# knarr

Knarr is a runtime taint-tracking / path-constraint collection layer that
turns Galette-tagged primitive values into Green-expression constraints and
ships them to a Z3-backed solver. Originally built on top of
[Phosphor](https://github.com/gmu-swe/phosphor); the current codebase is
ported to [Galette](https://github.com/neu-se/galette) so it runs on
Java 17+ (tested on OpenJDK 17).

## Architecture

```
┌───────────────────────────────────────────┐
│  application JVM (galette-instrumented)   │
│                                           │
│  Symbolicator.symbolic("x", 5)            │
│          │  Tainter.setTag(v, Tag.of(…))  │
│          ▼                                │
│  tagged primitive flows through user code │
│          │                                │
│          ▼   Galette's SymbolicExecution- │
│  branches, arith, String methods  Listener│
│          │                                │
│          ▼                                │
│  PathConstraintListener                   │
│          │  builds Green Expression       │
│          ▼                                │
│  PathConditionWrapper (AND chain)         │
│          │                                │
│          ▼  Symbolicator.dumpConstraints  │
│        [TCP 9090]                         │
└───────────┼───────────────────────────────┘
            │
            ▼
┌───────────────────────────────────────────┐
│  knarr-server (plain JVM + Z3 native)     │
│  receives Expression, solves, returns     │
│  a (variable → value) model               │
└───────────────────────────────────────────┘
```

## Requirements

- JDK 17 for building and running.
- Apache Maven 3.6+.
- Z3 4.8.9 (bundled under `z3-4.8.9-x64-ubuntu-16.04/` on Linux, or
  `z3-mac-bin/` on macOS).
- Two non-Central dependencies that you must install into your local Maven
  repository before the first build:
  - `edu.gmu.swe.greensolver:green:1.0-SNAPSHOT` — clone
    `gmu-swe/green-solver`, apply the `sourceDirectory=src` / `source=target=8`
    pom tweak (and add local-jar deps for the bundled z3/apfloat/trove jars),
    then `mvn -pl green -DskipTests install`.
  - `green-local:microsoft-z3:4.8.9` — install the Z3 java bindings jar from
    the Z3 distro with `mvn install:install-file`:
    ```
    mvn install:install-file -DgeneratePom=true \
        -Dfile=z3-4.8.9-x64-ubuntu-16.04/bin/com.microsoft.z3.jar \
        -DgroupId=green-local -DartifactId=microsoft-z3 \
        -Dversion=4.8.9 -Dpackaging=jar
    ```
- [Galette](https://github.com/neu-se/galette) built and installed locally.
  This repo vendors a fork under `galette/` (branch `knarr-integration`);
  build it first with `cd galette && mvn -DskipTests install`.

## Building

From the repo root:

```
mvn install -DskipTests -DskipITs
```

Builds `knarr/target/Knarr-0.0.3-SNAPSHOT.jar` and
`knarr-server/target/Knarr-Server-0.0.2-SNAPSHOT.jar` (shaded).

## Running tests

**Unit tests** (no instrumentation needed):

```
mvn -pl knarr-server test
```

Runs `Z3SolveTest`, which verifies the Green → Z3 pipeline end-to-end
on a plain JVM.

**Integration tests** (require an instrumented JDK — produced automatically
on first run by the `galette-maven-plugin:instrument` goal, cached thereafter):

```
mvn -pl knarr verify
```

Runs `SmokeITCase` (symbolic arithmetic, branches, arrays, Strings). The
`E2EServerITCase` is currently `@Disabled` pending a fix to the TCP
handshake between instrumented client and plain server.

## Using Knarr

```java
import edu.gmu.swe.knarr.runtime.PathConstraintListener;
import edu.gmu.swe.knarr.runtime.Symbolicator;
import edu.neu.ccs.prl.galette.internal.runtime.symbolic.SymbolicListener;

// 1. Install the listener once per JVM:
SymbolicListener.setListener(new PathConstraintListener());

// 2. Tag concrete values:
int x = Symbolicator.symbolic("x", 5);
String s = Symbolicator.symbolic("s", "hello");

// 3. Let your code run. Branches, arithmetic, array accesses, and
// String methods on tagged values flow into Knarr's path condition.
if (x > 3) {
    System.out.println(s.charAt(0));
}

// 4. Dump to the solver (requires knarr-server on 127.0.0.1:9090):
ArrayList<SimpleEntry<String, Object>> solution =
        Symbolicator.dumpConstraints(null);
// solution is a (variable name, satisfying value) list, or null if the
// path condition was empty.
```

Your application JVM must be launched with the Galette agent:

```
<INSTRUMENTED_JAVA_HOME>/bin/java \
    -Xbootclasspath/a:<galette-agent.jar> \
    -javaagent:<galette-agent.jar> \
    --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -cp <Knarr.jar>:<green.jar>:<your-app.jar> \
    YourApp
```

The `--add-opens java.base/java.lang=ALL-UNNAMED` flag is required for
`Symbolicator.symbolic(String, String)`, which reflects into `String.value`
to tag the backing character array.

## Running the solver

Start the knarr-server in a **separate JVM** (not the Galette-instrumented
one — there is a known conflict with Z3 native library initialization inside
instrumented JVMs):

```
LD_LIBRARY_PATH=z3-4.8.9-x64-ubuntu-16.04/bin \
    java -jar knarr-server/target/Knarr-Server-0.0.2-SNAPSHOT.jar
```

The server listens on 9090 by default. Clients connect to
`${SATServer:-127.0.0.1}:${SATPort:-9090}` (both JVM system properties).

## Known gaps

See individual commit messages for details.

- **onArrayStore does not get the stored value**. When a concrete value is
  written into a symbolic-indexed array, the listener only sees the old
  cell's value. Tagged-value stores work correctly.
- **String method coverage**. 7 core methods are masked
  (`equals`, `startsWith`, `endsWith`, `contains`, `indexOf`, `length`,
  `charAt`). `toLowerCase` / `toUpperCase` / `replace` / other string-returning
  methods flow through Galette's default tag-propagation but do not emit
  dedicated symbolic expressions.
- **`onIinc` semantics**. Correctly rewrites the local's shadow tag via a
  `var + increment` expression, but the SPI callback fires only when the
  local's pre-increment tag is non-empty.
- **Z3 init inside an instrumented JVM**. Green's Z3 service initialization
  SIGSEGVs in `java.util.logging.LogManager.readConfiguration` when called
  from a Galette-instrumented JVM on OpenJDK 17. Run the solver in a
  separate process (as knarr-server does).
- **E2E TCP test**. `E2EServerITCase` is currently disabled due to a
  broken-pipe in the object-stream handshake between an instrumented client
  and a plain server JVM. The two halves of the pipeline (constraint
  collection, Z3 solving) are covered by `SmokeITCase` and `Z3SolveTest`
  independently.

## Licensing

Knarr is released under the MIT License (see `LICENSE`). This branch
vendors a modified copy of Galette under `galette/` (branch
`knarr-integration`).
