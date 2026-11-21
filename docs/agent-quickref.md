# Quick reference for agents

Read [must-api.md](must-api.md), [conformance.md](conformance.md) and [modelling.md](modelling.md) once; use this page
while working.

## API

```java
MpProcess.Handle h = MpProcess.spawn("server", () -> { ... });   // role "server"
MpProcess.Pending p = MpProcess.create("client");                // Tid now, start later
MpProcess.Handle c = p.start(() -> { ... });
h.await(); c.await();                                            // main awaits every process
Channel.send(M, h.tid(), new Ping(1));                           // never blocks
Ping x = Channel.recv(M, Ping.class, m -> m.n() > 0);            // blocking, typed, guarded
Optional<MpMessage> y = Channel.tryRecv(M);                      // may be empty even if a message is pending
int n = Nondet.choose(List.of(1, 2));                            // index 0 explored first
MpProcess.assume(cond);                                          // false: this process ends blocked
@Tag("Ping") record Ping(int n) implements MpMessage {}          // every message: a tagged record
```

`M` is `CommunicationModel.P2P` (FIFO per sender) or `ASYN` (any order); `must` supports only these two.

## The ten rules

1. One sequential process per node; one step per handled message.
2. Main spawns or creates every process, starts them all before its first await, awaits all, and sends nothing.
3. Every message is a `@Tag` record of booleans, integers, strings, enums, `Optional`, `List`, `Set`, `Tid` and
   records; no `null`, floats, maps, arrays or mutable fields.
4. Use the same tags, field order and roles as the other program of the comparison.
5. Never keep or compute a `Tid`'s number; address processes through `Handle.tid()`, `Pending.tid()` or
   `MpProcess.self()`; a visible message may name main, visible and payload-nameable roles, never a daemon.
6. Guards are pure: no JMC call, no shared state, no exception.
7. Never catch JMC's exceptions (catch only your own types).
8. Reproduce the source, wrong behaviour included; every forced difference goes in the map with `file:line`.
9. Timers, pools, buses and model calls are abstracted (removed, collapsed or a `Nondet` over the outcomes); I/O is a
   scripted environment process.
10. End the run with a shutdown propagated to every process; leave nothing unexplained unread.

## Files a port comes with

- the rewrite (`MpProgram`) and, beside it, the shared messages and environment;
- the map (abstractions and deviations, `file:line` on both sides);
- the counts, the exports and the join outputs of the run it was checked with.

## Commands

From the JMC root, with the README's example (replace `example.PingPong` and the classes directory with yours):

```bash
CP=$(./gradlew -q -I docs/tools/mp-classpath.gradle :core:printMpClasspath)
javac -d docs/examples/build -cp "$CP" docs/examples/pingpong/PingPong.java
L="java -ea -cp $CP:docs/examples/build org.mpi_sws.jmc.mp.cli.MpLauncher"
$L example.PingPong must 0 1 rep-count                                       # count
$L example.PingPong must 0 1 rep-spec spec.jsonl                             # export
$L example.PingPong must 0 1 rep-online - 600 --spec spec.jsonl --witness w/ # online check, witnesses
$L example.PingPong must 0 1 rep-g impl.jsonl 600 --graphs impl.jsonl.graphs.jsonl
sed -n '2p' impl.jsonl.graphs.jsonl > g1.graph.json                          # one graph of the store
$L --replay g1.graph.json --allow-source-change                              # replay it
```

`--allow-source-change` is needed here because the example's classes have no `SOURCE.md5` manifest. Outside a git
clone, with `JMC_COMMIT` unset, the build identity is `unknown` too and the replay also needs `--allow-tool-change`.

Use a fresh report directory per run: the launcher deletes the plain files in it. Read the verdict from the
`verdict:` line (exit 0 for every verdict); exit 1 is a failed run, exit 2 a refused input.

## Classify every uncovered word

violation (a quoted sentence is broken) · twin difference · specification too narrow (widen it, record why) ·
harness artefact (fix the harness).

## Forbidden moves

- Repairing, completing or reordering the source's behaviour in the rewrite.
- Changing either program so that the tools agree.
- Reporting a finding without its witness, its map rows and its class.
- Relying on an exit code for a verdict, or on an export that is not `complete` as a specification.
