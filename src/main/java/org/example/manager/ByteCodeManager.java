package org.example.manager;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class ByteCodeManager {

    /**
     * @property {@link #path} - The path to the directory containing the .java files
     */
    public String path;

    /**
     * @property {@link #mainClassName} - The name of the main class
     */
    public String mainClassName;

    /**
     * Constructor for the {@link ByteCodeManager} class
     *
     * @param path - The path to the directory containing the .java files
     * @param mainClassName - The name of the main class
     */
    public ByteCodeManager(String path, String mainClassName) {
        this.mainClassName = mainClassName;
        this.path = path;
    }

    /**
     * Generate the bytecode for the .java files in the specified directory.
     * <br>
     * This method uses the JavaCompiler API to compile the .java files in the specified directory.
     * These directories are runtime, checker, and checker/strategy packages.
     *
     * @throws RuntimeException if the file manager cannot be closed
     */
    public void generateByteCode() {
        // Get the system Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                null,
                null,
                null
        )) {
            // Compile the .java files in the specified directories
            compileJavaFiles(compiler, fileManager, this.path);
            compileJavaFiles(compiler, fileManager, "src/main/java/org/example/runtime/");
            compileJavaFiles(compiler, fileManager, "src/main/java/org/example/checker/");
            compileJavaFiles(compiler, fileManager, "src/main/java/org/example/checker/strategy/");
        } catch (IOException e) {
            throw new RuntimeException("Error closing the file manager", e);
        }
    }

    /**
     * Compile the .java files in the specified directory.
     *
     * @param compiler - The {@link JavaCompiler} to use
     * @param fileManager - The {@link StandardJavaFileManager} to use
     * @param directoryPath - The path to the directory containing the .java files
     *
     * @throws IllegalArgumentException if the compiler, file manager, or directory path is null
     * @throws IllegalArgumentException if the directory path does not point to an existing directory
     * @throws IllegalArgumentException if the directory does not contain at least one .java file
     */
    private void compileJavaFiles(JavaCompiler compiler, StandardJavaFileManager fileManager, String directoryPath) {
        if (compiler == null || fileManager == null || directoryPath == null) {
            throw new IllegalArgumentException("Compiler, file manager, and directory path must not be null");
        }
        // Get the .java files from the directory
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Directory path must point to an existing directory");
        }
        File[] javaFiles = directory.listFiles((dir, name) -> name.endsWith(".java"));
        if (javaFiles == null || javaFiles.length == 0) {
            throw new IllegalArgumentException("Directory must contain at least one .java file");
        }
        // Get a compilation task from the compiler and compile the files
        Iterable<? extends JavaFileObject> compilationUnits =
                fileManager.getJavaFileObjectsFromFiles(Arrays.asList(javaFiles));
        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                null,
                null,
                null,
                compilationUnits
        );
        boolean isCompiledSuccessfully = task.call();
        // Print whether the compilation was successful
        System.out.println(
                "Compilation of files in " + directoryPath + " " +
                        (isCompiledSuccessfully ? "succeeded" : "failed")
        );
    }

    /**
     * Read the bytecode from the .class files in the specified directory.
     *
     * @return A map of class names to bytecode
     *
     * @throws IllegalArgumentException if the path is null
     * @throws IllegalArgumentException if the path does not point to an existing directory
     * @throws IllegalArgumentException if the directory does not contain at least one .class file
     * @throws IOException if an I/O error occurs
     */
    public Map<String, byte[]> readByteCode() {
        if (this.path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        File directory = new File(this.path);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Path must point to an existing directory");
        }
        File[] classFiles = directory.listFiles((dir, name) -> name.endsWith(".class"));
        if (classFiles == null || classFiles.length == 0) {
            return new HashMap<>();
        }
        Map<String, byte[]> classToBytecode = new HashMap<>();
        for (File classFile : classFiles) {
            try {
                byte[] bytecode = Files.readAllBytes(classFile.toPath());
                ClassReader classReader = new ClassReader(bytecode);
                String className = classReader.getClassName().replace("/", ".");
                classToBytecode.put(className, bytecode);
            } catch (IOException e) {
                System.err.println("Error reading bytecode from file: " + classFile.getPath());
                e.printStackTrace();
            }
        }
        return classToBytecode;
    }

    /**
     * Generate the .class files from the bytecode map.
     *
     * @param allBytecode - A map of class names to bytecode
     * @param path - The path to the directory to write the .class files to
     *
     * @throws IllegalArgumentException if the bytecode map or path is null
     * @throws IOException if an I/O error occurs
     */
    public void generateClassFile(Map<String, byte[]> allBytecode, String path) {
        if (allBytecode == null || path == null) {
            throw new IllegalArgumentException("Bytecode map and path must not be null");
        }
        for (Map.Entry<String, byte[]> entry : allBytecode.entrySet()) {
            String className = entry.getKey();
            byte[] bytecode = entry.getValue();
            String[] splitClassName = className.split("\\.");
            String simpleClassName = splitClassName[splitClassName.length - 1];
            try {
                Files.write(Path.of(path + simpleClassName + ".class"), bytecode);
            } catch (IOException e) {
                System.err.println("Error writing bytecode to file: " + path + simpleClassName + ".class");
                e.printStackTrace();
            }
        }
    }

    /**
     * Invoke the main method of the class with the specified package name.
     *
     * @param allBytecode - A map of class names to bytecode
     * @param packageName - The package name of the main class
     *
     * @throws IllegalArgumentException if the bytecode map or package name is null
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if an invalid argument is provided
     * @throws ClassNotFoundException if the class cannot be loaded
     * @throws NoSuchMethodException if the main method cannot be found
     * @throws InvocationTargetException if the main method cannot be invoked
     * @throws IllegalAccessException if the main method cannot be accessed
     */
    public void invokeMainMethod(Map<String,byte[]> allBytecode, String packageName) {
        if (allBytecode == null || packageName == null) {
            throw new IllegalArgumentException("Bytecode map and package name must not be null");
        }
        try {
            // Custom class loader to load the modified .class files
            ClassLoader customClassLoader = new ByteMapLoader(allBytecode);
            // Load the class with the main method using the custom class loader
            Class<?> mainClass = customClassLoader.loadClass(packageName + mainClassName);
            // Get the main method of the loaded class
            Method mainMethod = mainClass.getMethod("main", String[].class);
            // Prepare arguments for the main method
            String[] mainMethodArgs = {};  // Add any required arguments here
            // Invoke the main method
            while (true){
                mainMethod.invoke(null, (Object) mainMethodArgs);
            }
        } catch (IOException e) {
            System.err.println("Error reading bytecode file: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument provided: " + e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            System.err.println("Error loading the class: " + e.getMessage());
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            System.err.println("Error getting the main method: " + e.getMessage());
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            System.err.println("Error invoking the main method: " + e.getMessage());
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            System.err.println("Error accessing the main method: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generate the readable bytecode for the .class files in the specified directory.
     *
     * @param allBytecode - A map of class names to bytecode
     * @param path - The path to the directory to write the .txt files to
     *
     * @throws IllegalArgumentException if the bytecode map or path is null
     * @throws IOException if an I/O error occurs
     */
    public void generateReadableByteCode(Map<String, byte[]> allBytecode, String path) {
        if (allBytecode == null || path == null) {
            throw new IllegalArgumentException("Bytecode map and path must not be null");
        }
        for (Map.Entry<String, byte[]> entry : allBytecode.entrySet()) {
            String className = entry.getKey();
            byte[] bytecode = entry.getValue();
            try {
                ClassReader classReader = new ClassReader(bytecode);
                StringWriter stringWriter = new StringWriter();
                PrintWriter printWriter = new PrintWriter(stringWriter);
                ClassVisitor classVisitor = new TraceClassVisitor(null, new Textifier(), printWriter);
                classReader.accept(classVisitor, 0);
                String[] splitClassName = className.split("\\.");
                String simpleClassName = splitClassName[splitClassName.length - 1];
                try (FileWriter fileWriter = new FileWriter(path + simpleClassName + ".txt")) {
                    fileWriter.write(stringWriter.toString());
                }
            } catch (IOException e) {
                System.err.println("Error generating readable bytecode for class: " + className);
                e.printStackTrace();
            }
        }
    }
}