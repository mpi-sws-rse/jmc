package org.example;

import org.example.instrumentor.ByteCodeModifier;
import org.example.Transformer.ByteCodeManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class TestComplexCounter {

    public static void main(String[] args) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, ClassNotFoundException {
        String MainClass = "ComplexCounter";
        String MainPath = "src/main/java/org/example/concurrent/programs/complex/counter/";
        String packagePath = "org.example.concurrent.programs.complex.counter.";
        ByteCodeManager byteCodeManager = new ByteCodeManager(MainPath , MainClass);
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(allBytecode, packagePath+MainClass);
        byteCodeModifier.modifyThreadCreation();
        byteCodeModifier.modifyThreadStart();
        byteCodeModifier.modifyThreadRun();
        byteCodeModifier.modifyReadWriteOperation();
        byteCodeModifier.modifyMonitorInstructions();
        byteCodeModifier.addRuntimeEnvironment();
        byteCodeManager.generateClassFile(byteCodeModifier.allByteCode, MainPath);
        byteCodeManager.generateReadableByteCode(byteCodeModifier.allByteCode, MainPath);
        byteCodeManager.invokeMainMethod(byteCodeModifier.allByteCode, packagePath);
    }
}
