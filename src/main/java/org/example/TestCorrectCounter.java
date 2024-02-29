package org.example;

import org.example.checker.CheckerConfiguration;
import org.example.checker.CheckerConfiguration.ConfigurationBuilder;
import org.example.instrumenter.ByteCodeModifier;
import org.example.transformer.ByteCodeManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class TestCorrectCounter {
    public static void main(String[] args) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, ClassNotFoundException {

        CheckerConfiguration config = new CheckerConfiguration.ConfigurationBuilder().build();

        String MainClass = "CorrectCounter";
        String MainPath = "src/main/java/org/example/concurrent/programs/correct/counter/";
        String packagePath = "org.example.concurrent.programs.correct.counter.";
        ByteCodeManager byteCodeManager = new ByteCodeManager(config, MainPath, MainClass);
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
