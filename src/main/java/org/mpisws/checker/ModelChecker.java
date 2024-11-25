package org.mpisws.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.instrumenter.ByteCodeModifier;
import org.mpisws.manager.ByteCodeManager;

import java.util.Map;

/**
 * The ModelChecker class is responsible for managing the model checking process. It uses a
 * CheckerConfiguration to configure the process and a TestTarget to specify the program under test.
 */
public class ModelChecker {

    /**
     * @property {@link #logger} The logger for the ModelChecker class.
     */
    public static final Logger logger = LogManager.getLogger(ModelChecker.class);

    /**
     * @property {@link #configuration} The configuration for the model checking process.
     */
    CheckerConfiguration configuration;

    /**
     * @property {@link #target} The program under test.
     */
    TestTarget target;

    /**
     * Creates a new ModelChecker with the specified CheckerConfiguration and TestTarget.
     *
     * @param configuration The configuration for the model checking process.
     * @param testTarget The program under test.
     */
    public ModelChecker(CheckerConfiguration configuration, TestTarget testTarget) {
        this.configuration = configuration;
        this.target = testTarget;
    }

    /**
     * Creates a new ModelChecker with the specified CheckerConfiguration.
     *
     * @param configuration The configuration for the model checking process.
     */
    public ModelChecker(CheckerConfiguration configuration) {
        this.configuration = configuration;
        this.target = null;
    }

    /**
     * Checks the specified TestTarget. <br>
     * The check involves running the model checking process on the program under test.
     *
     * @param target The program under test.
     * @return true if the check was successful, false otherwise.
     */
    public boolean check(TestTarget target) {
        logger.trace("Starting checker");
        this.target = target;
        logger.debug("Checking {}", target.getTestClass());
        this.runBytecode(target);
        return true;
    }

    /**
     * Runs the model checking process on the specified TestTarget. <br>
     * The process involves generating bytecode for the program under test, modifying the bytecode,
     * and running the modified bytecode.
     *
     * @param target The program under test.
     */
    void runBytecode(TestTarget target) {
        this.configuration.saveConfig("src/main/resources/config/config.obj");
        String path = target.getTestPath();
        String classPath = target.getTestPackage() + "." + target.getTestClass();
        logger.trace("Generating bytecode for " + path + " " + classPath);
        ByteCodeManager byteCodeManager = new ByteCodeManager(path, target.getTestClass());
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(allBytecode, classPath);
        if (configuration.programType == ProgramType.SHARED_MEM) {
            modifyByteCodeSharedMem(byteCodeModifier);
        } else if (configuration.programType == ProgramType.MESSAGE_PASS) {
            modifyByteCodeMessagePass(byteCodeModifier);
        } else {
            logger.error("The program type is not supported");
            System.exit(-1);
        }
        byteCodeManager.generateClassFile(byteCodeModifier.allByteCode);
        if (this.configuration.verbose) {
            byteCodeManager.generateReadableByteCode(byteCodeModifier.allByteCode);
        }
        logger.debug("Running the modified bytecode");
        byteCodeManager.invokeMainMethod(byteCodeModifier.allByteCode, target.getTestPackage());
    }

    /**
     * Modifies the bytecode for the program under test. <br>
     * The modifications include adding runtime environment, modifying thread creation, start, run,
     * join, and monitor instructions, modifying assert statements, modifying symbolic evaluation,
     * modifying park and unpark operations, modifying synchronization, and modifying read and write
     * operations.
     *
     * @param byteCodeModifier The ByteCodeModifier to use for modifying the bytecode.
     */
    private void modifyByteCodeSharedMem(ByteCodeModifier byteCodeModifier) {
        byteCodeModifier.modifySyncMethod();
        byteCodeModifier.modifySymbolicEval();
        byteCodeModifier.modifyParkAndUnpark();
        byteCodeModifier.modifyExecutors();
        byteCodeModifier.modifyThreadCreation();
        byteCodeModifier.modifyThreadStart();
        byteCodeModifier.modifyThreadRun();
        byteCodeModifier.modifyReadWriteOperation();
        // byteCodeModifier.modifyMonitorInstructions();
        byteCodeModifier.modifyMonitorStatements();
        byteCodeModifier.modifyAssert();
        byteCodeModifier.modifyThreadJoin();
        byteCodeModifier.addRuntimeEnvironment();
    }

    private void modifyByteCodeMessagePass(ByteCodeModifier byteCodeModifier) {
        // TODO() : Complete
        byteCodeModifier.modifyParkAndUnpark();
        byteCodeModifier.modifyExecutors();
        byteCodeModifier.modifyThreadStart();
        byteCodeModifier.modifyThreadRun();
        byteCodeModifier.modifyAssert();
        byteCodeModifier.modifyThreadJoin();
        byteCodeModifier.addRuntimeEnvironment();
    }
}
