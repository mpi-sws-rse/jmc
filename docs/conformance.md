# Conformance checking

Conformance checking compares two message-passing programs that play the same roles: a **specification program**
(what the protocol allows) and an **implementation program** (a model of the code under check). Each program's
executions are summarised per visible role: the sequence of sends and receives with their values, the final status
(`done`, `blocked`, `err`) and the causal order between roles. The implementation conforms when every summary it
can produce is covered by a specification summary. JMC writes and reads the summary format shared with TraceForge,
specified in the daavar repository (<https://github.com/joulook/daavar>, `format/SUMMARY-FORMAT.md`).

Runnable example: the `PingPong` program of the [README](../README.md#conformance-checking-with-the-must-api), checked
against itself below.

## The workflow

1. **Write the specification program.** Rust with TraceForge is preferred (its stateful export, merge join and
   Decide make large specifications tractable); a Java specification works through the same format.
2. **Write the implementation program** with the [Must API](must-api.md), following [modelling.md](modelling.md).
   Both programs share the message vocabulary: the same tags, field order and roles.
3. **Count before you export**: run Must without an export file and look at `ran:` and `graphs:`.
4. **Export** each program: one summary file per program and configuration.
5. **Join** offline (`conf-join`, TraceForge) or check **online** (`--spec`, below).
6. **Read the verdict** and classify every uncovered word.

All commands below run from the JMC root with `CP` set as in the README:

```bash
CP=$(./gradlew -q -I docs/tools/mp-classpath.gradle :core:printMpClasspath):docs/examples/build
L="java -ea -cp $CP org.mpi_sws.jmc.mp.cli.MpLauncher"
```

## The launcher: `MpLauncher`

```
MpLauncher <program class[:args]> [strategy] [iterations] [seed] [reportPath] [exportFile] [timeoutSeconds]
           [--spec <file> [--stop-at-first] [--witness <file or dir/>]] [--graphs <store> [--graphs-limit N]]
           [--split W [--k K]]
MpLauncher --replay <graph.json> [--report <r.json>] [--allow-source-change] [--allow-tool-change] [<class[:args]>]
```

| Positional | Default | Meaning |
|---|---|---|
| program | | a `Runnable`; `Class:a,b` passes `"a","b"` to its `String...` constructor |
| strategy | `mp-random` | `must` (exhaustive) or `mp-random` (sampled) for message-passing programs |
| iterations | `1000` | `0` with `must`: unbounded, until the stack is exhausted or the timeout |
| seed | the clock | printed; used by `mp-random` |
| reportPath | `build/mp-launch/<class>` | **the launcher deletes the plain files in this directory**; use a fresh one |
| exportFile | none | the summary file; `-` or absent: no export (under `must`, counting mode) |
| timeoutSeconds | `600` | applies to `must` |

Options may stand anywhere after the program. `--split W [--k K]` (advanced) explores with `W` worker JVMs once the
search stack holds `K` entries (default `8·W`); it needs `must` and iterations `0`, writes the same export as the
sequential run, and refuses `--witness` and `--stop-at-first`. `--worker` is internal (the coordinator starts workers with it).

### Count, export, online check

```bash
$L example.PingPong must 0 1 rep-count                    # count: ran:, graphs:
$L example.PingPong must 0 1 rep-spec spec.jsonl          # export
$L example.PingPong must 0 1 rep-impl - 600 --spec spec.jsonl   # online check against spec.jsonl
```

The export's first line is the header (`format`, `tool`, `program`, `size`, `visible`, `roles`, `model`,
`enumeration`, ...), then one line per distinct summary with its multiplicity `n`, sorted by bytes. `enumeration`
says what the file allows one to conclude:

| enumeration | When | Use |
|---|---|---|
| `complete` | `must` exhausted the stack | a specification, and a full verdict |
| `bounded` | a `must` run that ended without failure before the exploration was exhausted (an iteration bound, or `--stop-at-first`) | a lower bound only |
| `aborted` | a failure, or the timeout | a lower bound only |
| `sampled` | an `mp-random` run that completed | a lower bound only |

A specification file must be `complete`, have the program's visible roles, roles and model, or it is refused.

### Reading an online verdict

| Line | Meaning |
|---|---|
| `verdict: conforms` | every execution was covered and the exploration finished |
| `verdict: reports` | at least one summary is not in the specification; `witness:` prints the first, `uncovered:` counts them |
| `verdict: partial` | nothing uncovered so far, but the exploration did not finish (timeout or bound) |

`--stop-at-first` stops at the first uncovered execution (bug-finder mode). `--witness <file>` writes that execution as
a text witness; `--witness <dir>/` writes `witness-<n>.txt` for each distinct uncovered summary.

### Graph stores and replay

`--graphs <store>` (with `must` and an export file) writes, beside the export, the full execution graph of every
distinct summary at its first occurrence (JSON Lines; `--graphs-limit N` keeps at most `N`). TraceForge's
`conf-join --witness-dir D` looks the uncovered words up in this store, so a join hands you the graphs of its
violations without a re-run. The store line `graph_store: written=W refused=R ms=T` reports the work. Without a
`SOURCE.md5` manifest beside the program's classes the store records `"source": "unknown"` and the launcher says so
on stderr (`graphs: source unknown: ...`); such graphs replay only with `--allow-source-change`.

`--replay g.json` re-executes one graph: every scheduling choice, received message and choice index is taken from the
graph, and every event is compared with it.

For the README's example (a graph of its store, and the same graph with its choice flipped):

```
replay: reproduced (12 events, 3 threads)                                                   exit 0
replay: deviation at (1, 2) thread "client": value: expected SEND(server, {"t":"Ping","f":[1]}),
        got SEND(server, {"t":"Ping","f":[2]})                                              exit 1
```

A graph from another tool, or an explicit program argument that differs from the graph's, is always refused (exit
2). `--allow-tool-change` accepts a different or unknown JMC build identity, `--allow-source-change` a different or
unknown benchmark source identity; without them those are refused too. The build's commit comes from the Gradle build
(`JMC_COMMIT`, else git; `unknown` when neither is available); the source identity is the `SOURCE.md5` manifest that the daavar build scripts write at the
root of the program's classes directory.

### Offline join (TraceForge)

```bash
conf-join spec.jsonl impl.jsonl                     # plain join: is every impl summary covered?
conf-join --decide spec.jsonl impl.jsonl            # also classifies each uncovered word
conf-join --decide --witness-dir w/ spec.jsonl impl.jsonl   # and writes w/<n>.graph.json from impl's store
```

`--decide` classifies every uncovered word as `violation`, `false-alarm` or `unresolved` (`--decide-budget N` bounds
the states explored per word); it changes neither the join's verdict nor its exit code. `conf-join` exits 0 when the
implementation conforms or the join gives no verdict, 1 when it reports a violation, and 2 on a refused input: read
the `verdict:` and `decide:` lines.

### Exit codes of `MpLauncher`

| Exit | When |
|---|---|
| 0 | the run completed; every online verdict (`conforms`, `reports`, `partial`), a timeout, a bug-finder stop; `replay: reproduced` |
| 1 | the run failed: a failed assertion or exception in any process, a shape or model fault, an export or store write failure, a split run that did not finish; `replay: deviation` |
| 2 | bad arguments or a refused input: unknown option or strategy, a class that cannot be loaded, a refused specification file, a refused replay |

An online verdict is read from the `verdict:` line, not from the exit code.

## The four classes of an uncovered word

Every uncovered word is classified before it is reported:

| Class | Meaning | What to do |
|---|---|---|
| violation | the implementation does something a quoted sentence of the specification forbids | report it, with the witness and the source lines |
| twin difference | two implementations differ where the specification allows both | record it; it is not a bug |
| specification too narrow | the specification forbids something its text allows | widen the specification and record why |
| harness artefact | the test harness, not the protocol, produced it (a shutdown order, a process left unread) | fix the harness, never the implementation |
