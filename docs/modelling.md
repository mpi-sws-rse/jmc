# Modelling an existing implementation

To check an implementation, you rewrite its protocol core as a Must program (an *implementation program*) and keep a
**map** that ties every part of the rewrite to the source. The rewrite must behave as the source does, including where
the source is wrong; only the concurrency machinery is abstracted.

Worked example: the A2A task benchmark in the daavar repository (<https://github.com/joulook/daavar>),
`benchmarks/a2a-task/java/bench/a2a_task/A2ATask.java`, a rewrite of the server core of a2aproject/a2a-java at commit
`f40664f8` (one task, one stream, a cancel racing the task's completion); line numbers below are at daavar commit
`229afdb`. From the JMC root, with `DAAVAR` set to a daavar checkout:

```bash
CP=$(./gradlew -q -I docs/tools/mp-classpath.gradle :core:printMpClasspath)
javac -d a2a-classes -cp "$CP" "$DAAVAR/benchmarks/a2a-task/java/bench/a2a_task/A2ATask.java"
java -cp "$CP:a2a-classes" org.mpi_sws.jmc.mp.launch.MpLauncher bench.a2a_task.A2ATask:impl,b must 0 1 rep-a2a
```

counts its executions (`impl,b`: the executor reports `Working` first, then completes or keeps working).

## What to rewrite and what to keep

| In the source | In the rewrite | A2A example |
|---|---|---|
| a node (server, client, replica) | one sequential process with one mailbox; a step per handled message | `server` handles `SendStream`, `Cancel`, `Get` and the executor's events, one per step |
| intra-process concurrency: thread pools, event buses, futures, consumer threads | collapsed into the step that handles the message; outputs to different subscribers interleave in any order | the event bus and per-subscriber consumers become "commit, then distribute in the same step" |
| a synchronous call that waits for another component | an external wait: send a request, end the step, continue when the answer arrives | the cancel calls the executor's `cancel` and waits: the server sends `CancelCb` and resumes on `Canceled` |
| timers and timeouts | removed, or a nondeterministic choice | the stream consumer's poll timeouts are removed; streams close only on a final event |
| a model or LLM call | a nondeterministic procedure over the outcomes the code distinguishes | the agent's executor: `call_llm` answers a plain message, or opens a task reported in one of several orders |
| external I/O, clients | scripted environment processes, shared by both programs | `client` and `exec` are the same in the Java rewrite, the Rust rewrite and the specification |
| identifiers (UUIDs, timestamps) | deterministic, correlation-preserving names | the task is named `t1` after the request that created it |
| error codes the comparison does not need | one opaque constant | every non-cancel error is `Err(req, "*")` |

## Faithfulness

- Reproduce the source's behaviour, wrong behaviour included. Never repair, complete or reorder. If the rewrite cannot
  avoid a difference, it is a **deviation** and goes in the map.
- Keep the vocabulary identical on both sides of a comparison: the same tags, the same field order, the same roles.
- Prefer a choice the source makes (`Nondet`) to a fixed script: if the code distinguishes outcomes, explore all of
  them.

## The map

The map is a document beside the rewrite with one row per abstraction and one per deviation, each with `file:line` in
the source and in the rewrite. Rows from the A2A map (source paths under a2a-java's
`server-common/src/main/java/org/a2aproject/sdk/server/`; rewrite lines in `A2ATask.java`, class `Server`):

| Source element | Where (a2a-java) | Collapsed into | Where (rewrite) | Behaviour lost |
|---|---|---|---|---|
| request threads | `requesthandlers/DefaultRequestHandler.java:702, 1022` | one mailbox message per request | `:675` `run` (the receive loop) | none here |
| agent-executor thread pool | `requesthandlers/DefaultRequestHandler.java:1364-1429` | `Exec` to the `exec` process | `:742` `onMessageSendStream` | pool exhaustion |
| the cancel calls the executor synchronously | `requesthandlers/DefaultRequestHandler.java:754-775` | `CancelCb` to `exec`, the step ends, `Canceled` resumes it | `:752` `onCancelTask`, `:775` `cancelCallbackReturned` | a throwing cancel callback |
| per-subscriber consumer threads with timed polls | `events/EventConsumer.java:101-302` | direct delivery inside the committing step | `:806` `processEvent`, `:860` `consumeAtStream` | streams that close by timeout |

A finding later points at its map rows: a reviewer reproduces it from the witness graph and the source lines.

## Outbound queues and non-blocking receives

A server that queues output and writes it later (a writer thread) keeps the queue in its state and flushes it at a
nondeterministic point inside its steps. Do not model the writer with `tryRecv` polling: an empty `tryRecv` is a
behaviour the strategy may choose even when a message is pending, and it shows up in the summary as a receive of
nothing.

## The harness

- Main spawns every process and awaits all of them; it sends and receives nothing (the export shape rules,
  [must-api.md](must-api.md#shape-rules-for-export)).
- The client ends the run: its last message is a shutdown that the server propagates to every other process, which
  then ends.
- Leave nothing unread at the end, or make the leftover explicit: a message nobody reads is still part of the sender's
  word.
- No daemons in an exported program unless they are invisible helpers that may end blocked.

## Checklist for a port

1. The rewrite (`*.java` with `MpProgram`), sharing the messages and the environment with the other side.
2. The map: abstractions and deviations with `file:line` on both sides.
3. A count run (`must`, no export) at the smallest size, then the exports, then the join.
4. For every uncovered word: its class (see [conformance.md](conformance.md#the-four-classes-of-an-uncovered-word)) and
   its map rows.
