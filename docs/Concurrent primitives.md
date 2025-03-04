JMC instruments the code at specific points during its execution to insert calls to the [[Runtime]]. The calls are inserted exactly at those points where the natural execution semantics allows two threads to be interleaved. Intuitively, natural execution semantics allows inter-leavings when shared objects and accessed (read or write). In this document, we list all the different shared objects along with a few Java specific features that JMC supports in its instrumentation.  
## 1. Locks

A process can `lock` and `unlock` on a share lock. The corresponding events pushed to the runtime are
- `lock` -  `LOCK_ACQUIRE_EVENT` 
- `unlock` - `LOCK_RELEASE_EVENT`
Lock and unlock just yields the control to the [[Scheduler]] and the Strategy should ensure no deadlocks
## 2. Monitors


## 3. Message Passing
## 4. Park and Un-Park
## 5. Futures
## 6. Atomic operations
## Currently unsupported

- [ ] Virtual threads