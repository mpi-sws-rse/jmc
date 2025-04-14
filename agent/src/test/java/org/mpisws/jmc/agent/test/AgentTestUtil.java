package org.mpisws.jmc.agent.test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class AgentTestUtil {

    @FunctionalInterface
    public interface ClassVisitorFactory {
        ClassVisitor create(ClassWriter cw);
    }

    public static void check(
            String testClassPath, String expectedClassPath, ClassVisitorFactory classVisitorFactory)
            throws Exception {

        // Print current directory for debugging
        System.out.println("Current directory: " + System.getProperty("user.dir"));

        ClassReader cr = new ClassReader(Files.readAllBytes(Path.of(testClassPath)));
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = classVisitorFactory.create(cw);
        cr.accept(cv, 0);

        byte[] transformed = cw.toByteArray();
        byte[] expected = Files.readAllBytes(Path.of(expectedClassPath));
        String error = "";
        if (transformed.length != expected.length) {
            error = "Transformed class length does not match expected class length";
        }
        if (error.isEmpty()) {
            for (int i = 0; i < transformed.length; i++) {
                if (transformed[i] != expected[i]) {
                    error = "Transformed class does not match expected class at byte " + i;
                    break;
                }
            }
        }
        if (!error.isEmpty()) {
            ClassReader cr2 = new ClassReader(expected);
            StringWriter sw = new StringWriter();
            cr2.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(sw)), 0);
            System.out.println("Expected class: ");
            System.out.println(sw.toString());

            ClassReader cr3 = new ClassReader(transformed);
            StringWriter sw2 = new StringWriter();
            cr3.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(sw2)), 0);
            System.out.println("Transformed class: ");
            System.out.println(sw2.toString());
            throw new RuntimeException(error);
        }
    }

    /**
     * CAUTION! Should be used to record the correct transformation of a class file. Not to be used
     * in any active test path, but to be used to build the test database.
     */
    public static void translateAndStore(
            String sourceClassPath, String targetClassPath, ClassVisitorFactory classVisitorFactory)
            throws Exception {
        ClassReader cr = new ClassReader(Files.readAllBytes(Path.of(sourceClassPath)));
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor cv = classVisitorFactory.create(cw);
        cr.accept(cv, 0);

        byte[] transformed = cw.toByteArray();
        Files.write(Path.of(targetClassPath), transformed);
    }
}
