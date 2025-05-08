The `ModelChecker` class encapsulates all the functionality of JMC and is the entry point of any program. It is initialized by specifying a target along with a [[Configuration]]. The `check` method starts the process.

The following is an example test run using the model checker

``` java
var t =  new TestTarget(  
	"org.mpisws.concurrent.programs.det.array",  
	"DetArray",  
	"main",  
	"src/test/java/org/mpisws/concurrent/programs/det/array"
);  

checker.configuration.strategyType = StrategyType.OPT_TRUST;  
...

assertTrue(checker.check(t), "...");
```

### Specifying the target

The target consists of 
1. The package name
2. The class name
3. The method to test
4. The path to the package

Currently, the user needs to specify the target parameters explicitly as strings. In Future, Specifying the target will mean just annotating the function. [[Backlog#^35f61b]]

## Running the Model Checker

Once the `check` method is invoked, the Model checker invokes the [[Byte code Modifier]] to instrument. And subsequently calls [[Byte code Modifier#Invoke Main]] to run the model checker.