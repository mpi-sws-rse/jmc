package org.mpisws.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The ByteMapLoader class extends the ClassLoader class and is responsible for loading
 * Java classes from bytecode. It maintains a map of class names to their bytecode, which is used to load classes.
 * The class provides functionality to load classes from a package, find a class by its name, and load a class by its name.
 * The class uses the ClassLoader API to load classes and the Files API to read bytecode from .class files.
 * The class requires a map of class names to their bytecode upon construction.
 * It also requires the path to the package and the name of the package when loading classes from a package.
 * The class throws a ClassNotFoundException if a class cannot be found or loaded.
 */
public class ByteMapLoader extends ClassLoader {
    /**
     * @property {@link #classes} - A map of class names to their bytecode
     */
    public Map<String, byte[]> classes;

    /**
     * The following constructor initializes the class loader with a map of class names to their bytecode
     * <br>
     * The constructor also loads classes from the runtime and checker packages.
     *
     * @param classes - A map of class names to their bytecode
     * @throws IOException - If there is an error reading the bytecode from the file
     */
    public ByteMapLoader(Map<String, byte[]> classes) throws IOException {
        if (classes == null) {
            throw new IllegalArgumentException("Classes map must not be null");
        }
        this.classes = classes;
        // Load classes from the runtime and checker packages
        loadClassesFromPackage("src/main/java/org/mpisws/runtime/", "org.mpisws.runtime");
        loadClassesFromPackage("src/main/java/org/mpisws/checker/", "org.mpisws.checker");
        loadClassesFromPackage("src/main/java/org/mpisws/checker/strategy/",
                "org.mpisws.checker.strategy");
        loadClassesFromPackage("src/main/java/org/mpisws/solver/", "org.mpisws.solver");
        loadClassesFromPackage("src/main/java/org/mpisws/symbolic/", "org.mpisws.symbolic");
        loadClassesFromPackage("src/main/java/org/mpisws/util/concurrent", "org.mpisws.util.concurrent");
    }

    /**
     * Loads classes from a package and adds them to the classes map
     *
     * @param packagePath - The path to the package
     * @param packageName - The name of the package
     * @throws IOException - If there is an error reading the bytecode from the file
     */
    private void loadClassesFromPackage(String packagePath, String packageName) throws IOException {
        Path dirPath = Paths.get(packagePath);
        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String className = packageName + "." + path.getFileName().toString().replace(
                                ".class", "");
                        try {
                            byte[] bytecode = Files.readAllBytes(path);
                            classes.put(className, bytecode);
                        } catch (IOException e) {
                            System.out.println("[Byte Map Loader Message] : Error reading bytecode from file: " + path);
                            e.printStackTrace();
                        }
                    });
        }
    }

    /**
     * Finds the class with the specified name
     * <br>
     * This method is the overridden version of the findClass method in the ClassLoader class. It finds the class with
     * the specified name and returns it. If the class with the specified name is not found, it throws a
     * {@link ClassNotFoundException}.
     *
     * @param name - The name of the class
     * @return The class with the specified name
     * @throws ClassNotFoundException - If the class with the specified name is not found
     */
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        if (name == null) {
            throw new IllegalArgumentException("Class name must not be null");
        }
        try {
            byte[] bytecode = classes.get(name.substring(name.lastIndexOf(".") + 1));
            if (bytecode == null) {
                return super.findClass(name);
            } else {
                return defineClass(name, bytecode, 0, bytecode.length);
            }
        } catch (ClassNotFoundException e) {
            System.out.println("[Byte Map Loader Message] : Error finding class: " + name);
            throw e;
        }
    }

    /**
     * Loads the class with the specified name
     * <br>
     * This method is the overridden version of the loadClass method in the ClassLoader class. It loads the class with
     * the specified name and returns it. If the class with the specified name is not found, it throws a
     * {@link ClassNotFoundException}.
     *
     * @param name - The name of the class
     * @return The class with the specified name
     * @throws ClassNotFoundException - If the class with the specified name is not found
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name == null) {
            throw new IllegalArgumentException("Class name must not be null");
        }
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> clazz = findLoadedClass(name);
            if (clazz == null) {
                byte[] classData = classes.get(name);
                if (classData != null) {
                    clazz = defineClass(name, classData, 0, classData.length);
                } else {
                    clazz = super.loadClass(name);
                }
            }
            return clazz;
        }
    }
}