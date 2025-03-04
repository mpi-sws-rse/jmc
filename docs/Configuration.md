The model checker configuration consists of the following parameters

| Parameter               | Description                                                                    | Default Value                                                                             |
| ----------------------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| `maxEventsPerExecution` | Maximum events to be executed in a single execution                            | 100                                                                                       |
| `progressReport`        | The interval at which progress will be reported                                | 0                                                                                         |
| `verbose`               | Verbosity (of ?)                                                               | `false`                                                                                   |
| `seed`                  | Random seed used throughout the model checker                                  | `new Random().nextLong()` <br>(should use system time to generate randomness by default)? |
| `strategyType`          |                                                                                | `Replay`                                                                                  |
| `buggyTracePath`        | The location to store any bug trace                                            | src/main/resources/buggyTrace <br>(should be inside build ideally)                        |
| `buggyTraceFile`        | The trace file name                                                            | `buggyTrace.obj`                                                                          |
| `executionGraphsPath`   | Location to store execution graphs                                             | src/main/resources/Visualized_Graphs                                                      |
| `solverType`            | Solver type used by the [[Symbolic execution engine]]                          | `SMTINTERPOL` (?)                                                                         |
| `programType`           | Shared memory or message passing? (ideally should be inferred)                 | Shared memory                                                                             |
| `graphExploration`      | (Isn't this something specific to the strategy?)                               | DFS                                                                                       |
| `solverAppraoch`        | The approach used by the solver for symbolic exploration. (Incremental/Simple) | `Incremental`                                                                             |
| `schedulingPolicy`      | Decides the priorities of the threads when there are many possibilities        | `FIFO`                                                                                    |
| `maxIterations`         | Maximum threshold of iterations that the model checker should run              | `10`                                                                                      |

