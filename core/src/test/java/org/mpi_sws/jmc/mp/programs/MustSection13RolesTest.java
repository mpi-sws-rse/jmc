package org.mpi_sws.jmc.mp.programs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.ClassNode;
import org.jetbrains.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.programs.mutants.RolesMisnamed;
import org.mpi_sws.jmc.mp.programs.mutants.T1Misnamed;
import org.opentest4j.AssertionFailedError;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tester test for J0 round 03 (reviewer finding m1): the role of <em>every</em> process, including
 * the sender-only role {@code t1}, which no runtime event carries (a role lives only in the
 * program's {@code MpProcess.Handle}; {@code START_EVENT} and {@code Envelope.src()} have task ids
 * only), so {@link MustSection13Test#checkShape} cannot see it.
 *
 * <p>Method: read the <em>compiled</em> program class with ASM (the copy shaded in
 * {@code kotlin-compiler}, already on core's classpath) and, in {@code run()}, pair each
 * {@code MpProcess.spawn(String, Runnable)} call with the string constant pushed before it (the
 * role) and the lambda passed to it (the body). Each body's word is the sequence of
 * {@code Channel.send/recv/tryRecv} and {@code MpProcess.spawn}/{@code Handle.await} calls in its
 * bytecode. Expected, from Must Example 3.1: {@code t1 -> [send, send]}, {@code t2 -> [recv, send]},
 * {@code t3 -> [recv, recv]}, and exactly these three spawns. Together with {@code checkShape}
 * (which observes at run time that the process doing the two sends sends 1 to the {@code t3}
 * process and 2 to the {@code t2} process) this pins all three roles to the paper's processes.
 *
 * <p>This is a static check of the bytecode, not a run-time observation; it relies on javac's
 * lambda shape (role constant, captured locals, {@code invokedynamic}, {@code invokestatic spawn}).
 * A role computed at run time instead of a constant is reported as a failure, not skipped.
 */
class MustSection13RolesTest {

    private static final String SPAWN_OWNER = "org/mpi_sws/jmc/api/mp/MpProcess";
    private static final String CHANNEL = "org/mpi_sws/jmc/api/mp/Channel";
    private static final String HANDLE = "org/mpi_sws/jmc/api/mp/MpProcess$Handle";

    private static ClassNode read(Class<?> c) throws Exception {
        String res = "/" + c.getName().replace('.', '/') + ".class";
        try (InputStream in = c.getResourceAsStream(res)) {
            assertNotNull(in, "class file " + res);
            ClassNode node = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        fail("no method " + name + desc + " in " + cn.name);
        return null;
    }

    private static List<String> word(MethodNode m) {
        List<String> w = new ArrayList<>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode mi) {
                if (mi.owner.equals(CHANNEL) && (mi.name.equals("send") || mi.name.equals("recv")
                        || mi.name.equals("tryRecv"))) {
                    w.add(mi.name);
                } else if (mi.owner.equals(SPAWN_OWNER) && mi.name.equals("spawn")) {
                    w.add("spawn");
                } else if (mi.owner.equals(HANDLE) && mi.name.equals("await")) {
                    w.add("await");
                }
            }
        }
        return w;
    }

    /** Role (as spawned in {@code run()}) to the word of the body spawned under it, in spawn order. */
    static Map<String, List<String>> roles(Class<?> program) throws Exception {
        ClassNode cn = read(program);
        MethodNode run = method(cn, "run", "()V");
        Map<String, List<String>> out = new LinkedHashMap<>();
        String lastString = null;
        Handle lastLambda = null;
        for (AbstractInsnNode i = run.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
                lastString = s;
            } else if (i instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length > 1
                    && indy.bsmArgs[1] instanceof Handle h) {
                lastLambda = h;
            } else if (i instanceof MethodInsnNode mi && mi.owner.equals(SPAWN_OWNER) && mi.name.equals("spawn")) {
                assertEquals("(Ljava/lang/String;Ljava/lang/Runnable;)L" + HANDLE + ";", mi.desc,
                        "main spawns with an explicit role");
                assertNotNull(lastString, "a constant role before spawn #" + (out.size() + 1));
                assertNotNull(lastLambda, "a lambda body passed to spawn of " + lastString);
                assertTrue(lastLambda.getOwner().equals(cn.name), "body defined in " + cn.name);
                assertTrue(!out.containsKey(lastString), "role " + lastString + " spawned once");
                out.put(lastString, word(method(cn, lastLambda.getName(), lastLambda.getDesc())));
                lastString = null;
                lastLambda = null;
            }
        }
        return out;
    }

    static void checkRoles(Class<?> program) throws Exception {
        Map<String, List<String>> r = roles(program);
        System.out.println("ROLES " + program.getSimpleName() + ": " + r);
        assertEquals(Map.of("t1", List.of("send", "send"), "t2", List.of("recv", "send"),
                "t3", List.of("recv", "recv")), r, "role -> process body (Must Example 3.1)");
    }

    private static void mutant(Class<?> program) {
        AssertionFailedError e = assertThrows(AssertionFailedError.class, () -> checkRoles(program));
        System.out.println("MUTANT VERDICT (roles): " + e.getMessage());
    }

    @Test
    void roles_mustSection13() throws Exception {
        checkRoles(MustSection13.class);
    }

    @Test
    void roles_detects_T1Misnamed() {
        mutant(T1Misnamed.class);
    }

    @Test
    void roles_detects_RolesMisnamed() {
        mutant(RolesMisnamed.class);
    }
}
