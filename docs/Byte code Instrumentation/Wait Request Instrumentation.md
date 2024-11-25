## Join

In the `modifyThreadJoin` method, we are adding the `RuntimeEnvironment.waitRequest` invocation after the `join`
method invocation.

### Example
Before:
```java
thread.join();
```

After:
```java
thread.join();
RuntimeEnvironment.waitRequest();
```


## READ/WRITE

In the `modifyReadWriteOperation` method, we are adding the `RuntimeEnvironment.waitRequest` invocation after the
read/write operation which are `GETFIELD`, `PUTFIELD`, `GETSTATIC`, `PUTSTATIC`. Note that the there is an implicit
call to the `RuntimeEnvironment.waitRequest` method before the read/write operation. This invocation has been hidden
in the `RuntimeEnvironment.readOperation` and `RuntimeEnvironment.writeOperation` methods.

### Example
Before:
```java
int x = obj.field;
```

After:
```java
RuntimeEnvironment.readOperation();
int x = obj.field;
RuntimeEnvironment.waitRequest();
```

Before:
```java
obj.field = 10;
```

After:
```java
RuntimeEnvironment.writeOperation();
obj.field = 10;
RuntimeEnvironment.waitRequest();
```

## Thread Run

In the `modifyThreadRun` method, we are adding the `RuntimeEnvironment.waitRequest` as the first statement in the
`run` method.

### Example
Before:
```java
public void run() {
    // code
}
```

After:
```java
public void run() {
    RuntimeEnvironment.waitRequest();
    // code
}
```

## Symbolic Evaluation (Deprecated)

In the `modifySymbolicEval` method, we are adding the `RuntimeEnvironment.waitRequest` invocation after the
invocation of the `evaluate` method from the `org/mpisws/symbolic/SymbolicFormula` class.

### Example

Before:
```java
SymbolicFormula formula = new SymbolicFormula();
formula.evaluate();
```

After:
```java
SymbolicFormula formula = new SymbolicFormula();
formula.evaluate();
RuntimeEnvironment.waitRequest();
```

## Monitor Instructions (Deprecated)

In the `modifyMonitorInstructions` method, we are adding the `RuntimeEnvironment.waitRequest` invocation after the 
`EnterMonitor` instruction has been called. Note that there is an implicit call to the `RuntimeEnvironment.waitRequest`
after the `ExitMonitor` instruction, which is hidden in the `RuntimeEnvironment.releasedMonitor` method.


### Example

Before:
```java
synchronized (obj) {
    // code
}
```

After:
```java
RuntimeEnvironment.enteredMonitor();
synchronized (obj) {
    RuntimeEnvironment.acquiredMonitor();
    RuntimeEnvironment.waitRequest();
    // code
    RuntimeEnvironment.exitMonitor();
}
RuntimeEnvironment.releasedMonitor();
```

## Monitor Statements

In the `modifyMonitorStatements` method, we are adding the `RuntimeEnvironment.waitRequest` invocation after the
`ExitMonitor` statement has been called. Note that there is an implicit calls to the `RuntimeEnvironment.waitRequest`
before the `EnterMonitor` statement, which is hidden in the `RuntimeEnvironment.acquireLockReq`.

### Example

Before:
```java
synchronized (obj) {
    // code
}
```

After:
```java
RuntimeEnvironment.acquireLockReq();
synchronized (obj) {
    RuntimeEnvironment.acquiredLock();
    // code
    RuntimeEnvironment.releaseLockReq();
}
RuntimeEnvironment.releasedLock();
RuntimeEnvironment.waitRequest();
```
