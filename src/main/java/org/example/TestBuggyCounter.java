package org.example;

import org.example.Instrumentor.ByteCodeModifier;
import org.example.Transformer.ByteCodeManager;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class TestBuggyCounter {
    public static void main(String[] args) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, ClassNotFoundException {

        String MainClass = "BuggyCounter";
        String MainPath = "src/main/java/org/example/concurrent/programs/wrong/counter/";
        String packagePath = "org.example.concurrent.programs.wrong.counter.";
        ByteCodeManager byteCodeManager = new ByteCodeManager(MainPath , MainClass);
        byteCodeManager.generateByteCode();
        Map<String, byte[]> allBytecode = byteCodeManager.readByteCode();
        ByteCodeModifier byteCodeModifier = new ByteCodeModifier(allBytecode, packagePath+MainClass);
        byteCodeModifier.addScheduler();
        byteCodeModifier.findAllThreads();
        byteCodeModifier.findAllStartThread();
        //byteCodeModifier.preRun();
        byteCodeModifier.findAllThreadsRun2();
        byteCodeManager.generateClassFile(byteCodeModifier.allByteCode, MainPath);
        byteCodeManager.generateReadableByteCode(byteCodeModifier.allByteCode, MainPath);
        byteCodeManager.invokeMainMethod(byteCodeModifier.allByteCode, packagePath);
    }
}
