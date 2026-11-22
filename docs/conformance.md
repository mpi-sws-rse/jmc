# Conformance checking

Conformance checking compares two message-passing programs that play the same roles: a **specification program**
(what the protocol allows) and an **implementation program** (a model of the code under check). Each program's
executions are summarised per visible role: the sequence of sends and receives with their values, the final status
(`done`, `blocked`, `err`) and the causal order between roles. The implementation conforms when every summary it
can produce is covered by a specification summary. JMC writes and reads the summary format shared with TraceForge,
specified in the daavar repository (<https://github.com/joulook/daavar>, `format/SUMMARY-FORMAT.md`).

Runnable example: the `PingPong` program of the [README](../README.md#conformance-checking), checked
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
MpLauncher count  --program <class[:args]> [run options]
MpLauncher export --program <class[:args]> [run options] --out <file> [--graphs <store> [--graphs-limit N]]
MpLauncher online --program <class[:args]> [run options] --spec <file> [--stop-at-first] [--no-pairing-check]
    [--witness <file or dir/>]
                  [--out <file> [--graphs <store> [--graphs-limit N]]]
MpLauncher replay --graph <graph.json> [--report <r.json>] [--allow-source-change] [--allow-tool-change]
                  [--program <class[:args]>]
MpLauncher --help | <command> --help
```

| Run option | Default | Meaning |
|---|---|---|
| `--program <class[:args]>` | | a `Runnable`; `Class:a,b` passes `"a","b"` to its `String...` constructor; `export` and `online` need an `MpProgram` |
| `--strategy <s>` | `must` | `must` (exhaustive; `p2p` and `asyn` only) or `mp-random` (sampled; any model); any other strategy is refused |
| `--model <m>` | the program's | `p2p` (alias `fifo`), `asyn` (`bag`), `cd` (`causal`) or `mbox`; refused if it differs from the model an `MpProgram` declares, and `cd`/`mbox` under `must`; printed canonically on the `model:` line |
| `--iterations N` | `0` for `must`, `1000` for `mp-random` | `0` (`must` only): unbounded, until the stack is exhausted or the timeout |
| `--seed N` | the clock | printed; used by `mp-random` |
| `--report-dir <dir>` | `build/mp-launch/<class>` | **the launcher deletes the plain files in this directory**; use a fresh one |
| `--timeout S` | `600` | seconds; applies to `must` |

Each option is given at most once; an option a command does not take, or a missing required one, is a usage error
(exit 2). Advanced: `--split W [--k K]` explores with `W` worker JVMs once the search stack holds `K` entries (default
`8·W`); it needs `must` and iterations `0`, writes the same export as the sequential run, and refuses `--witness` and
`--stop-at-first`. `--worker` is internal (the coordinator starts workers with it). Sizing: one item runs in one worker
JVM, and items are uneven (the first ones, the earliest branches, often hold most of the executions). A worker keeps
an item's distinct summaries in its heap and streams the item's outcomes to the run directory (about 94 bytes per
execution on disk), so a large item needs heap for its summaries and disk for its outcomes. A larger `K` makes more,
smaller items; a larger `W` alone does not. A worker that runs out of memory has its item retried once, on the restarted
worker or another live one (not the JVM that failed); a second failure aborts the run, naming the item and how many
items were committed.

The positional form, `MpLauncher <program class[:args]> [strategy=mp-random] [iterations=1000] [seed] [reportPath]
[exportFile] [timeoutSeconds] [options]` and `MpLauncher --replay <graph.json> [...] [<class[:args]>]`, is still
accepted and is deprecated; it prints no `model:` line.

### Count, export, online check

```bash
$L count  --program example.PingPong                       # count: ran:, graphs:
$L export --program example.PingPong --out spec.jsonl      # export
$L online --program example.PingPong --spec spec.jsonl     # online check against spec.jsonl
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

The specification is read only in the form both tools' writers emit, as TraceForge's `conf-join` reads it: the header
holds only the known keys, each once, compact, in the writers' order, and each further line is its normal form,
optionally followed by `n`. A blank line, an unknown key, other spacing or key order, another spelling of a value
(`"\u0041"` for `"A"`), a repeated summary (`line N repeats line M`) or a header without `program`, `size` or `tool`
is refused, naming the line (exit 2). Lines end at LF or CRLF.

Before the run, the pairing check verifies what a meaningful comparison needs, with the rows and wording of
`conf-join --check-pairing`. Each failure is printed as `MpLauncher: pairing: <row>: …`, then `MpLauncher: refused:
the pairing check failed (K checks; --no-pairing-check skips it)` (exit 2); warnings as `MpLauncher: pairing:
warning: <row>: …`.

| Row | The check fails when |
|---|---|
| C2 | the specification's `visible` list is not byte-sorted, or its `visible` or `roles` list differs from the program's |
| C3 | the models differ, or a model is not `p2p` or `asyn` |
| C4 | the specification is not `complete` |
| C6 | the specification repeats a line |
| C10 | a count exceeds 2^31−1 or a word has more than 2^31−1 events |
| C13 | the specification uses a tag with a field count the program does not declare for it |

The program's tags are those of the `@Tag` records and the enums in the program class's package that implement
`MpMessage`, and of the records, enums and sealed interfaces their components reach (through `List`, `Set` and
`Optional` too). A tag in one side only is a warning (C13), as is a value tagged `ThreadId` or `Tid` (C18: identities
must be sent as role names). A line the reader refuses is left to the reader's own refusal; a header it refuses
stops the check, so the reader's refusal is printed alone, with no warning before it. A refusal by the pairing check or the reader,
like a refused replay, prints no usage line.

`--no-pairing-check` skips the pairing check, but the reader still refuses C2, C4, C6 and C10 and, of C3, models
that differ, on its own, in its own words (C6 in the same words), with exit 2. What is really skipped: C13 (an arity
the program does not declare reaches the comparison), C18 (no warning is printed) and the C3 rule that a model is
`p2p` or `asyn` (in the named form the launcher refuses other models anyway).

### Graph stores and replay

`--graphs <store>` (with `must` and `--out`) writes, beside the export, the full execution graph of every
distinct summary at its first occurrence (JSON Lines; `--graphs-limit N` keeps at most `N`). TraceForge's
`conf-join --witness-dir D` looks the uncovered words up in this store, so a join hands you the graphs of its
violations without a re-run. The store line `graph_store: written=W refused=R ms=T` reports the work. Without a
`SOURCE.md5` manifest beside the program's classes the store records `"source": "unknown"` and the launcher says so
on stderr (`graphs: source unknown: ...`); such graphs replay only with `--allow-source-change`.

`replay --graph g.json` re-executes one graph: every scheduling choice, received message and choice index is taken from the
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

The named form (`MpLauncher count|export|online|replay`, also listed by `--help`):

| Exit | When |
|---|---|
| 0 | conforms, or nothing to report: the exploration was exhausted (`count`, `export`), `verdict: conforms`, `replay: reproduced` |
| 1 | a violation: `verdict: reports` (also after a `--stop-at-first` stop), a failed assertion or an exception in any process or in main, in any iteration including the last, a receive guard that threw; `replay: deviation` |
| 2 | refused: a usage error, a strategy that is not a message-passing one, a model or value the strategy does not support, a broken rule of the API or of the export format, a refused specification file or failed pairing check, store, witness target or replay, an output that cannot be written |
| 3 | partial or incomplete: a timeout, an iteration bound reached before the exploration was exhausted, `mp-random` (sampled), `verdict: partial`, a split run that did not finish |

The deprecated positional form keeps its statuses: 0 the run completed, whatever the online verdict (read the
`verdict:` line), including a timeout and a bug-finder stop, and `replay: reproduced`; 1 the run failed (a failed
assertion or exception in any process, a shape or model fault, an export or store write failure, a split run that
did not finish) and `replay: deviation`; 2 bad arguments or a refused input.

## The four classes of an uncovered word

Every uncovered word is classified before it is reported:

| Class | Meaning | What to do |
|---|---|---|
| violation | the implementation does something a quoted sentence of the specification forbids | report it, with the witness and the source lines |
| twin difference | two implementations differ where the specification allows both | record it; it is not a bug |
| specification too narrow | the specification forbids something its text allows | widen the specification and record why |
| harness artefact | the test harness, not the protocol, produced it (a shutdown order, a process left unread) | fix the harness, never the implementation |
