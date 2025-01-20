package org.mpisws.manager;

//import java.io.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;


/**
 * The ByteCodeManager class is responsible for managing the bytecode of Java classes.
 * It provides functionality to generate bytecode from .java files, read bytecode from .class files,
 * write bytecode to .class files, invoke the main method of a class, and generate human-readable bytecode.
 * The class uses the JavaCompiler API to compile .java files and the ASM library to read and write bytecode.
 * It also uses reflection to invoke the main method of a class.
 * The class requires the path to the directory containing the .java files and the name of the main class.
 */
public class ByteCodeManager {

    private static final Logger LOGGER = LogManager.getLogger(ByteCodeManager.class);

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
     * @param path          The path to the directory containing the .java files
     * @param mainClassName The name of the main class
     */
    public ByteCodeManager(String path, String mainClassName) {
        this.mainClassName = mainClassName;
        this.path = path;
    }

    /**
     * Generate the bytecode for the .java files in the specified directory.
     * <p>
     * This method uses the JavaCompiler API to compile the .java files in the specified directory.
     * These directories are runtime, checker, and checker/strategy packages.
     * </p>
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
            compileJavaFilesInDirectory(compiler, fileManager, new File(this.path));
            compileJavaFilesInDirectory(compiler, fileManager, new File("src/main/java/org/mpisws/runtime/"));
            compileJavaFilesInDirectory(compiler, fileManager, new File("src/main/java/org/mpisws/checker/"));
            compileJavaFilesInDirectory(compiler, fileManager, new File("src/main/java/org/mpisws/checker/strategy/"));
            compileJavaFilesInDirectory(compiler, fileManager, new File("src/main/java/org/mpisws/symbolic/"));
            compileJavaFilesInDirectory(compiler, fileManager, new File("src/main/java/org/mpisws/solver/"));
            compileJavaFilesInDirectory(compiler, fileManager, new File("src/main/java/org/mpisws/util/concurrent/"));
            //compileKotlinFilesInDirectory(new File("src/main/kotlin/dpor/"));
            //compileKotlinFilesInDirectory(new File("src/main/kotlin/executionGraph/"));
        } catch (IOException e) {
            throw new RuntimeException("Error closing the file manager", e);
        }
    }

    /**
     * Compile the .java files in the specified directory.
     * <p>
     * This method recursively compiles the .java files in the specified directory and its subdirectories.
     * </p>
     *
     * @param compiler    - The {@link JavaCompiler} to use
     * @param fileManager - The {@link StandardJavaFileManager} to use
     * @param directory   - The directory containing the .java files
     */
    private void compileJavaFilesInDirectory(JavaCompiler compiler, StandardJavaFileManager fileManager,
                                             File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    compileJavaFilesInDirectory(compiler, fileManager, file);
                } else if (file.getName().endsWith(".java")) {
                    compileJavaFiles(compiler, fileManager, file.getPath());
                }
            }
        }
    }

    private void compileKotlinFilesInDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    compileKotlinFilesInDirectory(file);
                } else if (file.getName().endsWith(".kt")) {
                    compileKotlinFile(file.getPath());
                }
            }
        }
    }

    private void compileKotlinFile(String filePath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("kotlinc", filePath, "-d", "out");
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                LOGGER.info("Compilation of file {} succeeded", filePath);
            } else {
                LOGGER.error("Compilation of file {} failed", filePath);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error compiling Kotlin file: {}", filePath, e);
        }
    }

    /**
     * Compiles a single .java file.
     *
     * @param compiler    - The JavaCompiler instance to use for compilation.
     * @param fileManager - The StandardJavaFileManager instance to manage the file objects used in the compilation.
     * @param filePath    - The path to the .java file to be compiled.
     * @throws IllegalArgumentException if the compiler, file manager, or filePath is null.
     * @throws IllegalArgumentException if the filePath does not point to an existing .java file.
     */
    private void compileJavaFiles(JavaCompiler compiler, StandardJavaFileManager fileManager, String filePath) {
        // Ensure the compiler, file manager, and file path are not null
        if (compiler == null || fileManager == null || filePath == null) {
            throw new IllegalArgumentException("Compiler, file manager, and file path must not be null");
        }
        // Ensure the file path points to an existing .java file
        File javaFile = new File(filePath);
        if (!javaFile.exists() || !filePath.endsWith(".java")) {
            throw new IllegalArgumentException("File path must point to an existing .java file");
        }
        // Get a compilation task from the compiler and compile the file
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(javaFile);
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, compilationUnits);
        boolean isCompiledSuccessfully = task.call();
        // Print the result of the compilation
        LOGGER.info("Compilation of file {} {}", filePath, isCompiledSuccessfully ? "succeeded" : "failed");
    }

    /**
     * read the bytecode from the .class files in the specified directory.
     *
     * @return A map of class names to bytecode
     * @throws IllegalArgumentException if the path is null
     * @throws IllegalArgumentException if the path does not point to an existing directory
     * @throws IllegalArgumentException if the directory does not contain at least one .class file
     * @throws IOException              if an I/O error occurs
     */
    public Map<String, byte[]> readByteCode() {
        if (this.path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        File directory = new File(this.path);
        if (!directory.exists() || !directory.isDirectory()) {
            LOGGER.error("Path does not exist : {}/{}", directory, path);
            throw new IllegalArgumentException("Path must point to an existing directory");
        }
        Map<String, byte[]> classToBytecode = new HashMap<>();
        readByteCodeInDirectory(directory, classToBytecode);
        return classToBytecode;
    }

    /**
     * read the bytecode from the .class files in the specified directory and its subdirectories.
     *
     * @param directory       - The directory to read the .class files from
     * @param classToBytecode - A map of class names to bytecode
     * @throws IOException if an I/O error occurs
     */
    private void readByteCodeInDirectory(File directory, Map<String, byte[]> classToBytecode) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    readByteCodeInDirectory(file, classToBytecode);
                } else if (file.getName().endsWith(".class")) {
                    try {
                        byte[] bytecode = Files.readAllBytes(file.toPath());
                        ClassReader classReader = new ClassReader(bytecode);
                        String className = classReader.getClassName().replace("/", ".");
                        classToBytecode.put(className, bytecode);
                    } catch (IOException e) {
                        LOGGER.error("Error reading bytecode from file: {}", file.getPath());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Generate the .class files from the bytecode map.
     *
     * @param allBytecode - A map of class names to bytecode
     * @throws IllegalArgumentException if the bytecode map is null
     * @throws IOException              if an I/O error occurs
     */
    public void generateClassFile(Map<String, byte[]> allBytecode) {
        if (allBytecode == null) {
            throw new IllegalArgumentException("Bytecode map must not be null");
        }
        for (Map.Entry<String, byte[]> entry : allBytecode.entrySet()) {
            String className = entry.getKey();
            byte[] bytecode = entry.getValue();
            String classPath = className.replace(".", "/");
            try {
                LOGGER.info("Writing bytecode to file: src/test/java/{}.class", classPath);
                Files.write(Path.of("src/test/java/" + classPath + ".class"), bytecode);
            } catch (IOException e) {
                LOGGER.error("Error writing bytecode to file: src/test/java/{}.class", classPath);
                e.printStackTrace();
            }
        }
    }

    /**
     * Invoke the main method of the class with the specified package name.
     *
     * @param allBytecode - A map of class names to bytecode
     * @param packageName - The package name of the main class
     * @throws InvocationTargetException if the main method encounters an exception
     * @throws IllegalArgumentException  if the bytecode map or package name is null
     * @throws IOException               if an I/O error occurs
     * @throws ClassNotFoundException    if the class cannot be found
     * @throws NoSuchMethodException     if the main method cannot be found
     * @throws IllegalAccessException    if the main method cannot be accessed
     * @throws InvocationTargetException if the main method cannot be invoked
     */
    public void invokeMainMethod(Map<String, byte[]> allBytecode, String packageName) {
        if (allBytecode == null || packageName == null) {
            throw new IllegalArgumentException("Bytecode map and package name must not be null");
        }
        try {
            // Custom class loader to load the modified .class files
            ClassLoader customClassLoader = new ByteMapLoader(allBytecode);
            // Load the class with the main method using the custom class loader
            Class<?> mainClass = customClassLoader.loadClass(packageName + "." + mainClassName);
            // Get the main method of the loaded class
            Method mainMethod = mainClass.getMethod("main", String[].class);
            // Prepare arguments for the main method
            String[] mainMethodArgs = {};  // Add any required arguments here
            // Invoke the main method
            Finished finished = saveFinishObject();
            while (!finished.terminate) {
                try {
                    mainMethod.invoke(null, (Object) mainMethodArgs);
                    finished = loadFinishObject();
                } catch (InvocationTargetException e) {
                    if (e.getTargetException() instanceof HaltExecutionException) {
                        LOGGER.error("The Halt Execution Exception happened");
                        finished = loadFinishObject();
                    } else {
                        // Handle other exceptions
                        LOGGER.error("Error invoking the main method: {}", e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            state(finished);
            makeResultFile(finished);
        } catch (IOException e) {
            LOGGER.error("Error reading bytecode file: {}", e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid argument provided: {}", e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error loading the class: {}", e.getMessage());
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            LOGGER.error("Error getting the main method: {}", e.getMessage());
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            LOGGER.error("Error accessing the main method: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    public void invokeMainMethod(Map<String, byte[]> allBytecode, String packageName, int[][] inputIntegers) {
        if (allBytecode == null || packageName == null) {
            throw new IllegalArgumentException("Bytecode map and package name must not be null");
        }
        try {
            int numOfExecutions = 0;
            int numOfBlockedExecutions = 0;
            int numOfCompletedExecutions = 0;
            long timeTaken = 0L;
            // Read the size of the first dimension of the inputIntegers array
            int size = inputIntegers.length;
            int index = 0;
            // Invoke the main method
            Finished finished = null;
            while (index < size) {
                finished = saveFinishObject();
                // Custom class loader to load the modified .class files
                ClassLoader customClassLoader = new ByteMapLoader(allBytecode);
                // Load the class with the main method using the custom class loader
                Class<?> mainClass = customClassLoader.loadClass(packageName + "." + mainClassName);
                // Get the main method of the loaded class
                Method mainMethod = mainClass.getMethod("main", String[].class);
                // Prepare arguments for the main method
                String[] mainMethodArgs = Arrays.stream(inputIntegers[index])
                        .mapToObj(String::valueOf)
                        .toArray(String[]::new); // Add any required arguments here
                while (!finished.terminate) {
                    try {
                        mainMethod.invoke(null, (Object) mainMethodArgs);
                        finished = loadFinishObject();
                    } catch (InvocationTargetException e) {
                        if (e.getTargetException() instanceof HaltExecutionException) {
                            LOGGER.error("The Halt Execution Exception happened");
                            finished = loadFinishObject();
                        } else {
                            // Handle other exceptions
                            LOGGER.error("Error invoking the main method: {}", e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                numOfExecutions += finished.numOfExecutions;
                numOfBlockedExecutions += finished.numOfBlockedExecutions;
                numOfCompletedExecutions += finished.numOfCompletedExecutions;
                timeTaken += finished.timeTaken;
                index++;
            }
            Finished finishResult = new Finished();
            finishResult.numOfExecutions = numOfExecutions;
            finishResult.numOfBlockedExecutions = numOfBlockedExecutions;
            finishResult.numOfCompletedExecutions = numOfCompletedExecutions;
            finishResult.timeTaken = timeTaken;
            if (finished != null) {
                state(finished);
                makeResultFile(finishResult);
            } else {
                LOGGER.error("Finished object is null");
                System.exit(0);
            }
        } catch (IOException e) {
            LOGGER.error("Error reading bytecode file: {}", e.getMessage());
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid argument provided: {}", e.getMessage());
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            LOGGER.error("Error loading the class: {}", e.getMessage());
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            LOGGER.error("Error getting the main method: {}", e.getMessage());
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            LOGGER.error("Error accessing the main method: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void makeResultFile(Finished finished) {
        // Define the directory and file name
        String directoryPath = "src/main/resources/ObjectStore/";
        String fileName = "result";

        // Create the directory if it doesn't exist
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            System.out.println("[RuntimeEnvironment] The directory does not exist...");
            System.exit(0);
        }

        // Create the file and write to it
        File file = new File(directoryPath + fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Write some lines to the file
            LOGGER.info("Executions finished: {}", finished.numOfExecutions);
            LOGGER.info("Executions blocked: {} ", finished.numOfBlockedExecutions);
            LOGGER.info("Elapsed Time: {} nano seconds", finished.timeTaken);
            LOGGER.info("Elapsed Time: {} seconds", finished.timeTaken / 1_000_000_000);
            String res = finished.numOfExecutions + " $ " + finished.numOfCompletedExecutions + " $ " + finished.numOfBlockedExecutions + " $ " + finished.timeTaken;
            writer.write(res);
            writer.newLine();
        } catch (IOException e) {
            System.out.println("[RuntimeEnvironment] An error occurred while writing to the result file.");
            System.exit(0);
        }
    }

    /**
     * Save the finished object to a file
     *
     * @return the finished object
     */
    public Finished saveFinishObject() {
        Finished finished = new Finished();
        // Serialize the Boolean object
        try (FileOutputStream fileOut = new FileOutputStream("src/main/resources/finish/finish.obj");
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(finished);
            return finished;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Finished saveFinishObject(int i) {
        Finished finished = new Finished();
        // Serialize the Boolean object
        try (FileOutputStream fileOut = new FileOutputStream("src/main/resources/finish/finish" + i + ".obj");
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(finished);
            return finished;
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Load the finished object from a file
     *
     * @return the finished object
     */
    public Finished loadFinishObject() {
        try (FileInputStream fileIn = new FileInputStream("src/main/resources/finish/finish.obj");
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            return (Finished) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Finished loadFinishObject(int i) {
        try (FileInputStream fileIn = new FileInputStream("src/main/resources/finish/finish" + i + ".obj");
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            return (Finished) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Generate the readable bytecode for the .class files in the specified directory.
     *
     * @param allBytecode - A map of class names to bytecode
     * @throws IllegalArgumentException if the bytecode map is null
     * @throws IOException              if an I/O error occurs
     */
    public void generateReadableByteCode(Map<String, byte[]> allBytecode) {
        if (allBytecode == null) {
            throw new IllegalArgumentException("Bytecode map must not be null");
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
                String classPath = className.replace(".", "/");
                try (FileWriter fileWriter = new FileWriter("src/test/java/" + classPath + ".txt")) {
                    fileWriter.write(stringWriter.toString());
                }
            } catch (IOException e) {
                LOGGER.error("Error generating readable bytecode for class: {}", className);
                e.printStackTrace();
            }
        }
    }

    /**
     * Print the state of the model checking process
     */
    public void state(Finished finished) {
        LOGGER.info("The Model Checking process has finished");
        if (finished.type == FinishedType.SUCCESS) {
            LOGGER.info("The program is thread-safe");
        } else if (finished.type == FinishedType.DEADLOCK) {
            LOGGER.info("The program has a potential deadlock");
        } else if (finished.type == FinishedType.BUG) {
            LOGGER.info("The program is not thread-safe");
        }
    }
}