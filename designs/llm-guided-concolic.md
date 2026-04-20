# LLM-guided concolic mutator — JSON protocol + wiring sketch

The `llm-guided` mutator is structurally identical to the `guided` mutator
in `knarr-concolic/.../pilot/PilotRunner.java`: it ranks candidate branch
flip targets by (a) whether the site has ever resolved only one way in
this session and (b) PC depth. The only difference is that the final
pick is delegated to an external subprocess rather than `ranked.get(0)`.

The subprocess seam lives behind the `KNARR_LLM_RANKER` environment
variable. When unset, `llm-guided` falls back to local ranking (identical
behaviour to `guided`) — so CI never shells out to an LLM. The pilot
ITCases skip the `llm-guided` column entirely unless `KNARR_LLM_RANKER`
is set, keeping the default `mvn test` invocation deterministic.

## Protocol

### stdin (ranker input)

Written by the pilot as a single UTF-8 JSON object, then the pipe is
closed. The ranker has up to 10s to write a response to stdout before
the pilot kills the subprocess and falls back to local ranking.

```json
{
  "pilot": "tar",
  "iter": 3,
  "candidates": [
    {
      "idx": 42,
      "site": "java/lang/StringLatin1::indexOf:123",
      "taken": 7,
      "notTaken": 0
    },
    ...
  ]
}
```

- `pilot`: `"tar"` or `"ant"`.
- `iter`: zero-based session iteration counter.
- `candidates`: at most 32 entries, already pre-ranked by the local
  heuristic (one-way sites first, deepest-first tiebreak). The ranker
  is free to re-order or pick anywhere in the list.
- `site`: a stable fingerprint derived from the branch expression
  itself (toString of the un-negated cmp, hashed + prefixed when
  long). An early iteration attempted a true `class/method:line`
  via `StackWalker`, but under Galette instrumentation a walker on
  every branch emission turned a 30s Ant iter into >15min; the
  expression fingerprint yields an identical heuristic (same branch
  gets the same key) at microsecond cost.
- `taken` / `notTaken`: cumulative counts across the whole session for
  this `site` — a `one-way` site has one of these at zero.

The current outcome bucket ("ENTRY_READ bucket=0" etc.) is not yet
included — add it to the JSON in `PilotRunner.askLlmRanker` if a future
ranker wants to condition on recent behaviour.

### stdout (ranker response)

Single-line JSON. Anything else (empty output, multi-line, invalid JSON,
`idx` out of range, non-zero exit, timeout) triggers fallback to local
ranking.

```json
{"idx": 42, "reason": "deep branch, never seen not-taken — likely unlocks new state"}
```

- `idx`: must be a valid index into the current iter's branch list
  (0 &le; idx &lt; `branchCount`), NOT necessarily one of the 32 listed
  candidates. The ranker can pick any index.
- `reason`: free-form; logged but otherwise unused. The pilot side's
  telemetry prints `reason=llm` for any LLM-picked flip regardless of
  what the ranker wrote.

## One-liner sketch (`~/bin/claude-rank-concolic.sh`)

The seam is designed so a single shell script can shell out to Claude
Code (or any other CLI) and echo back the JSON. Example:

```bash
#!/usr/bin/env bash
set -euo pipefail
# Read the pilot's JSON on stdin.
payload=$(cat)
# Ask Claude to rank the candidates. `claude --print` streams the final
# reply to stdout; we strip any preamble and grab the first JSON object.
prompt=$(cat <<EOF
You are ranking concolic-execution branch flip targets. Input:
$payload

Pick ONE idx from the candidates list that, if flipped, is most likely
to open a qualitatively new outcome (new exception class, new parser
state, new control-flow region). Prefer sites in parser decision logic
over sites in library internals; prefer one-way sites; break ties by
deepest idx. Reply with ONE LINE of JSON: {"idx": <n>, "reason": "<short>"}.
EOF
)
echo "$prompt" | claude --print --output-format text 2>/dev/null \
  | grep -oE '\{"idx":[[:space:]]*-?[0-9]+[^}]*\}' | head -1
```

Wire it up:

```bash
export KNARR_LLM_RANKER=~/bin/claude-rank-concolic.sh
mvn -pl knarr-concolic -Dcrochet.dualJdk=/tmp/jdk-dual-ga-first \
    -Dtest='TarPilotITCase,AntPilotITCase' test
```

With that env var set the ITCases add an `llm-guided` column and the
output tables grow from 5 to 6 mutators.

## What this scaffold deliberately does NOT do

- Does **not** include a Claude API client or any LLM SDK. The Java
  side only shells out; the prompt / model / cache lives entirely in
  the user's subprocess. If that subprocess decides to hit an HTTP API,
  read a cache, or consult a local model — none of that is visible to
  the pilot.
- Does **not** retry or batch. One subprocess fork per iteration, capped
  at 10 seconds. A future version could batch ("pick flips for iters
  3..12 in one call") but the current pilots only run 10 iterations per
  mutator so the overhead is already bounded.
- Does **not** learn. Each subprocess call sees the cumulative
  `site → (taken, notTaken)` counts but no history of which flips
  succeeded vs. hit UNSAT. If a ranker wants memory, it has to keep
  state in its own filesystem.

## Measurement hooks

Every LLM-picked flip logs:

```
<PILOT>_GUIDED_PICK iter=N idx=I reason=llm site=...
<PILOT>_GUIDED_SUMMARY ... llm_picks=X llm_fallbacks=Y ...
```

`llm_fallbacks` counts iterations where the subprocess failed (timeout,
bad exit, unparseable output). A future evaluation can compare
`guided` vs `llm-guided` branch / outcome counts directly from the
per-pilot output snapshots under `knarr-concolic/target/*-pilot-*-output.txt`.
