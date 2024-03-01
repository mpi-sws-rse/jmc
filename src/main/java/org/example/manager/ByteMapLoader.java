package org.example.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ByteMapLoader extends ClassLoader{
    public Map<String, byte[]> classes;

    public ByteMapLoader(Map<String, byte[]> classes) throws IOException {
        this.classes = classes;
        Path classFilePath = Paths.get("src/main/java/org/example/runtime/RuntimeEnvironment.class");
        byte[] bytecode = Files.readAllBytes(classFilePath);
        classes.put("org.example.runtime.RuntimeEnvironment", bytecode);
        classFilePath = Paths.get("src/main/java/org/example/runtime/SchedulerThread.class");
        bytecode = Files.readAllBytes(classFilePath);
        classes.put("org.example.runtime.SchedulerThread", bytecode);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = classes.get(name.substring(name.lastIndexOf(".") + 1));
        if (bytecode == null) {
            System.out.println("Class not found: " + name);
            return super.findClass(name);
        } else {
            System.out.println("Class found: " + name);
            return defineClass(name, bytecode, 0, bytecode.length);
        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                byte[] classData = classes.get(name);
                if (classData != null) {
                    c = defineClass(name, classData, 0, classData.length);
                } else {
                    c = super.loadClass(name);
                }
            }
            return c;
        }
    }

}
