package org.example.checker;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.instrumenter.ByteCodeModifier;
import org.example.manager.ByteCodeManager;

/**
 * The ModelChecker class is responsible for managing the model checking process.
 * It uses a CheckerConfiguration to configure the process and a TestTarget to specify the program under test.
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
     * @param testTarget    The program under test.
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
     * Checks the specified TestTarget.
     * <br>
     * The check involves running the model checking process on the program under test.
     *
     * @param target The program under test.
     * @return true if the check was successful, false otherwise.
     */
    public boolean check(TestTarget target) {
        logger.trace("Starting checker");
        this.target = target;
        System.out.println("Checking " + target.getTestClass());
        this.run(target);
        return true;
    }

    /**
     * Runs the model checking process on the specified TestTarget.
     * <br>
     * The process involves generating bytecode for the program under test, modifying the bytecode, and running the
     * modified bytecode.
     *
     * @param target The program under test.
     */
    void run(TestTarget target) {
        this.configuration.saveConfig("src/main/resources/config/config.obj");
        String path = target.getTestPath();
        String classPath = target.getTestPackage() + target.getTestClass();
        logger.trace("Generating bytecode for " + path + " " + classPath);
        ByteCodeManager byteCodeManager = new ByteCodeManager(path, target.getTestClass());
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(allBytecode, classPath);
        modifyByteCode(byteCodeModifier);
        byteCodeManager.generateClassFile(byteCodeModifier.allByteCode, path);
        if (this.configuration.verbose) {
            byteCodeManager.generateReadableByteCode(byteCodeModifier.allByteCode, path);
        }
        System.out.println("Running the modified bytecode");
        createFinishObject();
        byteCodeManager.invokeMainMethod(byteCodeModifier.allByteCode, target.getTestPackage());
    }

    /**
     * Modifies the bytecode for the program under test.
     * <br>
     * The modifications include adding runtime environment, modifying thread creation, start, run, join, and monitor
     * instructions, and adding read and write operations.
     *
     * @param byteCodeModifier The ByteCodeModifier to use for modifying the bytecode.
     */
    private void modifyByteCode(ByteCodeModifier byteCodeModifier) {
        byteCodeModifier.modifyThreadCreation();
        byteCodeModifier.modifyThreadStart();
        byteCodeModifier.modifyThreadRun();
        byteCodeModifier.modifyReadWriteOperation();
        byteCodeModifier.modifyMonitorInstructions();
        byteCodeModifier.modifyThreadJoin();
        byteCodeModifier.modifyAssert();
        byteCodeModifier.addRuntimeEnvironment();
    }

    private void createFinishObject() {
        Boolean finish = Boolean.FALSE;
        // Serialize the Boolean object
        try (FileOutputStream fileOut = new FileOutputStream("src/main/resources/finish/finish.obj");
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(finish);

        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
