package org.example.manager;

import org.example.checker.CheckerConfiguration;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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

public class ByteCodeManager {
    public CheckerConfiguration config;
    public String path;
    public String mainClassName;

    public ByteCodeManager(CheckerConfiguration config, String path, String mainClassName) {
        this.config = config;
        this.mainClassName = mainClassName;
        this.path = path;
    }

    public void generateByteCode() {
        // Get the system Java compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // Get a StandardJavaFileManager
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        // Get the files (ending with .java) from the path
        File path = new File(this.path); // Replace with your path
        File[] files = path.listFiles((dir, name) -> name.endsWith(".java"));

        // Get a compilation task from the compiler and compile the files
        Iterable<? extends JavaFileObject> compilationUnits = fileManager
                .getJavaFileObjectsFromFiles(Arrays.asList(files));
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, compilationUnits);
        boolean success = task.call();

        File schedulerPath = new File("src/main/java/org/example/runtime/");
        File[] schedulerFiles = schedulerPath.listFiles((dir, name) -> name.endsWith(".java"));
        Iterable<? extends JavaFileObject> schedulerCompilationUnits = fileManager
                .getJavaFileObjectsFromFiles(Arrays.asList(schedulerFiles));
        JavaCompiler.CompilationTask schedulerTask = compiler.getTask(null, fileManager, null, null, null,
                schedulerCompilationUnits);
        boolean schedulerSuccess = schedulerTask.call();
        // Print whether the compilation was successful
        System.out.println("Compilation " + (success && schedulerSuccess ? "succeeded" : "failed"));

        // Close the file manager
        try {
            fileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, byte[]> readByteCode() throws IOException {
        Map<String, byte[]> classToBytecode = new HashMap<>();

        // Get the .class files from the path
        File path = new File(this.path); // Replace with your path
        File[] files = path.listFiles((dir, name) -> name.endsWith(".class"));

        // Read each .class file and store it in the map
        for (File file : files) {
            byte[] bytecode = Files.readAllBytes(file.toPath());

            // Create a ClassReader to read the class file
            ClassReader classReader = new ClassReader(bytecode);

            // Get the class name from the ClassReader, which includes the package name
            String className = classReader.getClassName().replace("/", ".");

            classToBytecode.put(className, bytecode);
        }
        return classToBytecode;
    }

    public void generateClassFile(Map<String, byte[]> allBytecode, String path) {
        for (String className1 : allBytecode.keySet()) {
            try {
                String[] splitClassName = className1.split("\\.");
                String simpleClassName = splitClassName[splitClassName.length - 1];
                Files.write(Path.of(path + simpleClassName + ".class"), allBytecode.get(className1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    /*
     * TODO(): Following method is deprecated and should be removed
     */
    // public void generateClassFile(Map<String, byte[]> allBytecode, String path) {
    // for (String className1 : allBytecode.keySet()) {
    // try {
    // Files.write(Path.of(path + className1 + ".class"),
    // allBytecode.get(className1));
    // } catch (IOException e) {
    // throw new RuntimeException(e);
    // }
    // }
    // }

    /*
     * TODO(): Following method is deprecated and should be removed
     * public void generateClassFile(List<byte[]> allBytecode, String pathByteCode)
     * {
     * try {
     * Files.write(Path.of(path + className + ".class"), byteCode);
     * } catch (IOException e) {
     * throw new RuntimeException(e);
     * }
     */

    public void invokeMainMethod(Map<String, byte[]> allBytecode, String packageName) throws ClassNotFoundException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        // Custom class loader to load the modified .class files
        ClassLoader customClassLoader = new ByteMapLoader(allBytecode);

        // Load the class with the main method using the custom class loader
        Class<?> mainClass = customClassLoader.loadClass(packageName + mainClassName);

        // Get the main method of the loaded class
        Method mainMethod = mainClass.getMethod("main", String[].class);

        // Prepare arguments for the main method
        String[] mainMethodArgs = {}; // Add any required arguments here

        // Invoke the main method
        mainMethod.invoke(null, (Object) mainMethodArgs);
    }

    public void generateReadableByteCode(Map<String, byte[]> allBytecode, String path) throws IOException {
        for (String className1 : allBytecode.keySet()) {
            ClassReader cr = new ClassReader(allBytecode.get(className1));
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ClassVisitor cv = new TraceClassVisitor(null, new Textifier(), pw);

            cr.accept(cv, 0);

            // Write the human-readable bytecode to a file
            String[] splitClassName = className1.split("\\.");
            String simpleClassName = splitClassName[splitClassName.length - 1];
            try (FileWriter fileWriter = new FileWriter(path + simpleClassName + ".txt")) {
                fileWriter.write(sw.toString());
            }
        }
    }
}
