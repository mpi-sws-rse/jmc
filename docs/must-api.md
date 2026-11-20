# The Must message-passing API

JMC model checks message-passing programs written against a small API in `org.mpi_sws.jmc.api.mp`. A program is a
set of processes that exchange immutable messages over channels with a declared delivery model, and that may make
nondeterministic choices. The `must` strategy explores every distinct execution (one per execution graph); the
`mp-random` strategy samples executions.

Runnable example: [`examples/pingpong/PingPong.java`](examples/pingpong/PingPong.java); how to run it is in the
[README](../README.md#conformance-checking-with-the-must-api).

## Processes: `MpProcess`

| Call | What it does |
|---|---|
| `MpProcess.spawn(role, body)` | creates and starts a process named `role`; returns a `Handle` |
| `MpProcess.create(role)` then `pending.start(body)` | allocates the process (its `Tid` is usable at once, e.g. as a destination) and starts it later; only the creator may start it, at most once, and a created process that is never started fails the execution |
| `MpProcess.spawnDaemon(body)`, `MpProcess.createDaemon()` | a daemon: no role, never visible, not awaitable |
| `handle.await()` | waits for the process to end; rethrows on the awaiting thread whatever ended the process body (e.g. an `AssertionError`) |
| `handle.tid()`, `pending.tid()` | the process's identity, valid in the current execution only |
| `MpProcess.self()` | the caller's `Tid` |
| `MpProcess.assume(cond)` | `assume(false)` ends the calling process as *blocked*; the rest of the program continues |

Rules the runtime enforces:
- every spawned process must be awaited (or have ended) before main returns; JMC does not wait for spawned tasks;
- awaiting a process that ended blocked blocks the caller, and the execution ends blocked;
- a program with daemons runs under `MpProcess.runMain`, which the launcher does for you: after main's body
  returns, main waits until no other task can move, so daemons finish their work;
- a daemon that ends blocked on a receive does not make the execution blocked; blocked any other way (for example
  by `assume(false)`) it does;
- no API method may be named `join` or `yield` (the instrumentation rewrites them).

## Channels: `Channel`

```java
Channel.send(M, dst, new Ping(1));                    // never blocks
MpMessage m  = Channel.recv(M);                       // blocking, any message
Ping p       = Channel.recv(M, Ping.class);           // blocking, typed
Ping q       = Channel.recv(M, Ping.class, x -> x.n() > 0);   // blocking, typed with a guard
Optional<MpMessage> r = Channel.tryRecv(M);           // non-blocking
```

- **Models.** `M` is a `CommunicationModel`: `P2P` (FIFO per sender, like TCP), `ASYN` (any pending message may be
  received, like UDP), `CD` (causal delivery) and `MBOX` (one send order per destination). TraceForge calls the
  first three `fifo`, `bag` and `causal`; the summary header writes `p2p`, `asyn`, `cd`. The `must` strategy
  supports **P2P and ASYN only**; a send or receive under another model fails the run with
  `must: task N uses model M, which must does not support (p2p and asyn only)`. `mp-random` supports all four.
- **`tryRecv`** returns `Optional.empty()` for "nothing received", and the strategy may choose that outcome *even when
  a message is pending*: an empty `tryRecv` is a behaviour, not a failure. Must explores the empty outcome first.
- **Guards** must be pure and deterministic, read only their argument and captured locals or final fields, never call
  the JMC API (not even `MpProcess.self()`; capture the id before the receive) and never throw. A guard that throws
  fails the receiver.
- **Control flow.** The API's exceptions are JMC control flow. Never catch them: catch nothing broader than your own
  types around a send, receive or choice.

## Which strategy supports what

| | `must` | `mp-random` | every other strategy |
|---|---|---|---|
| `P2P`, `ASYN` | yes, exhaustive | yes, sampled | no: a message-passing event fails the run |
| `CD`, `MBOX` | no: the run fails with the message above | yes | no |
| `Monitors` (send/receive notifications, which use `CD`) | no | yes | no |
| export, online check, graph store | yes | export only | no |

## Choices: `Nondet`

```java
int n       = Nondet.choose(List.of(1, 2, 3));   // one of the options
int i       = Nondet.chooseIndex(3);             // 0, 1 or 2
boolean b   = Nondet.chooseBoolean();            // index 0 is true
```

The option list is finite and ordered; index 0 is explored first by Must. A singleton list (or `chooseIndex(1)`)
returns its only option without a choice event.

## Messages: `MpMessage` and `@Tag`

A message is a record that implements `MpMessage`, is immutable, compares by value and refers to processes only
through `Tid`. For export (conformance checking) every record carries a `@Tag` that both programs of a comparison
declare identically:

```java
@Tag("Ping") public record Ping(int n) implements MpMessage {}
```

| Allowed in a message | Encoded as |
|---|---|
| `boolean`, `byte`, `short`, `int`, `long` (and boxes) | JSON boolean or integer |
| `String` | JSON string |
| an `enum` constant | `{"t":"<name>","f":[]}` |
| a `@Tag` record (nested too) | `{"t":"<tag>","f":[fields in declaration order]}` |
| `Optional` | `{"t":"Some","f":[v]}` or `{"t":"None","f":[]}` |
| `List` / `Set` | array in order / array sorted by encoded bytes |
| `Tid` | the role name of that process |

Refused at export, with a message naming the field: `null` (use `Optional`), `float`/`double`, maps, arrays, other
classes, and records without `@Tag`. Strings must be valid Unicode, and the elements of a set must encode
differently (a set holding both `Integer` 1 and `Long` 1 is refused).

## Programs: `MpProgram`

A program to export implements `MpProgram` (in JMC's test sources, package `org.mpi_sws.jmc.mp.launch`):

| Method | Meaning |
|---|---|
| `programName()`, `size()` | written in the export header; identical in both programs of a comparison |
| `model()` | the model of every send and receive |
| `rolesInSpawnOrder()` | the declared roles, distinct, in any order (the name is historical); `declaredRoles()` returns it |
| `visibleRoles()` | the roles whose events are compared; default: all declared roles |
| `payloadNameableRoles()` | invisible roles whose `Tid` may appear in a visible message; default: none |

Roles are non-empty ASCII names, not `main`, not starting with `~`. Invisibility is a projection: the events of an
invisible process are explored but not written to the summary.

## Shape rules for export

An exported program has a fixed shape, which `must` checks (each violation fails the run with the message shown):

| Rule | Message (after `must: `) |
|---|---|
| main sends nothing | `main performs a send` |
| main receives nothing | `main performs a receive` |
| only main starts ordinary processes | `process N was spawned by process P` |
| main starts every process before its first await | `process N was spawned after main's first await` |
| only main awaits | `process N awaits another process` |
| main awaits every process | `process(es) [..] were never awaited by main` |
| every process has a declared role, spawned once | `process X has no role; ...`, `process X has role R, which is not declared [..]`, `role R is spawned more than once`, `declared role R was never spawned` |

Daemons are exempt from the spawn and await rules: any process may start a daemon, at any time.

## Identities

A `Tid` is valid only in the execution that created it. Do not keep `Tid`s across executions, store their numbers,
or compute ids: ids restart in every execution and are reused. To address a process, use its `Handle`, its `Pending`
or, inside the process, `MpProcess.self()`. In an exported message a `Tid` is written as the role of its process: a
visible message may name main, a visible role or a payload-nameable role, never a daemon.

## Failures, `finally`, assertions

- A process whose body throws fails the execution, whether or not it is awaited; the run reports it as
  `process X (task n) failed: ...` and the launcher exits 1.
- A failed assertion in any process, or in main (also in the last execution), makes the run fail with exit 1. Java's
  `assert` statements need `-ea` on the launcher's JVM; `throw new AssertionError(...)` works without it. The
  launcher tests `exit1_assertionInProcess`, `exit1_assertionInMain` and `exit1_assertionInMain_onLastIteration`
  (`core/src/test/java/org/mpi_sws/jmc/mp/launch/MpLauncherTest.java`) prove this.
- A `finally` block of a process may run after the process was stopped; it must not block, loop, or use this API.
