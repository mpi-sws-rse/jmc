package org.example.checker;

import java.util.Random;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.instrumenter.ByteCodeModifier;
import org.example.transformer.ByteCodeManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModelChecker {
    public static final Logger logger = LogManager.getLogger(ModelChecker.class);

    CheckerConfiguration configuration;
    TestTarget target;

    public ModelChecker(CheckerConfiguration c, TestTarget t) {
        this.configuration = c;
        this.target = t;
    }

    public ModelChecker(CheckerConfiguration c) {
        this.configuration = c;
        this.target = null;
    }

    void run(TestTarget target) throws IOException, ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {
        String path = target.getTestPath();
        String classPath = target.getTestPackage() + target.getTestClass();
        logger.trace("Generating bytecode for " + path + " " + classPath);

        ByteCodeManager byteCodeManager = new ByteCodeManager(this.configuration, path, target.getTestClass());
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(this.configuration, allBytecode, classPath);
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

    public boolean check(TestTarget t) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException, IOException {
        logger.trace("Starting checker");
        this.target = t;
        this.run(t); // fix exceptions
        return true;
    }
}
