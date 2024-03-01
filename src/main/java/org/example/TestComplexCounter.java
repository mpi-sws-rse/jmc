package org.example;

import org.example.checker.CheckerConfiguration;
import org.example.instrumenter.ByteCodeModifier;
import org.example.manager.ByteCodeManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class TestComplexCounter {

    public static void main(String[] args) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, ClassNotFoundException {
        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().build();

        String MainClass = "ComplexCounter";
        String MainPath = "src/main/java/org/example/concurrent/programs/complex/counter/";
        String packagePath = "org.example.concurrent.programs.complex.counter.";
        ByteCodeManager byteCodeManager = new ByteCodeManager(config, MainPath , MainClass);
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(config, allBytecode, packagePath+MainClass);
        byteCodeModifier.modifyThreadCreation();
        byteCodeModifier.modifyThreadStart();
        byteCodeModifier.modifyThreadRun();
        byteCodeModifier.modifyReadWriteOperation();
        byteCodeModifier.modifyMonitorInstructions();
        byteCodeModifier.modifyThreadJoin();
        byteCodeModifier.modifyAssert();
        byteCodeModifier.addRuntimeEnvironment();
        byteCodeManager.generateClassFile(byteCodeModifier.allByteCode, MainPath);
        byteCodeManager.generateReadableByteCode(byteCodeModifier.allByteCode, MainPath);
        byteCodeManager.invokeMainMethod(byteCodeModifier.allByteCode, packagePath);
    }
}
