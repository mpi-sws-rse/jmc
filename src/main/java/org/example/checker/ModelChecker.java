package org.example.checker;

import java.util.Random;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.Instrumentor.ByteCodeModifier;
import org.example.Transformer.ByteCodeManager;

public class ModelChecker {
    public static final Logger logger = LogManager.getLogger(ModelChecker.class);

    CheckerConfiguration configuration;
    TestTarget target;
    Random rng;

    public ModelChecker(CheckerConfiguration c, TestTarget t) {
        this.configuration = c;
        this.target = t;
        rng = new Random(configuration.seed);
    }

    public ModelChecker(CheckerConfiguration c) {
        this.configuration = c;
        this.target = null;
        rng = new Random(configuration.seed);
    }

    void run(TestTarget target) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String path = target.getTestPath();
        String classPath = target.getTestPath() + target.getTestClass();
        ByteCodeManager byteCodeManager = new ByteCodeManager(path , target.getTestClass());
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(allBytecode, classPath);
        byteCodeModifier.modifyThreadCreation();
        byteCodeModifier.modifyThreadStart();
        byteCodeModifier.modifyThreadRun();
        byteCodeModifier.modifyReadWriteOperation();
        byteCodeModifier.modifyMonitorInstructions();
        byteCodeModifier.modifyThreadJoin();
        byteCodeModifier.modifyAssert();
        byteCodeModifier.addRuntimeEnvironment();
        byteCodeManager.generateClassFile(byteCodeModifier.allByteCode, path);

        if (this.configuration.verbose) {
            byteCodeManager.generateReadableByteCode(byteCodeModifier.allByteCode, path);
        }
        
        byteCodeManager.invokeMainMethod(byteCodeModifier.allByteCode, target.getTestPackage());
    }

    public boolean check(TestTarget t) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        logger.info("Starting checker");
        this.target = t;
        this.run(t); // fix exceptions
        return true;
    }
}
