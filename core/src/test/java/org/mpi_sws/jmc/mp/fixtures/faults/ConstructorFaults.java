package org.mpi_sws.jmc.mp.fixtures.faults;

/**
 * A program whose constructor fails.
 * <ul>
 *   <li>{@code direct}: the constructor throws {@code IllegalArgumentException("bad size 0")};</li>
 *   <li>{@code nested}: the constructor uses a class whose static initialiser throws
 *       {@code IllegalStateException("static init boom")}, so the constructor fails with an
 *       {@code ExceptionInInitializerError} whose cause is that exception.</li>
 * </ul>
 * Argument: {@code ConstructorFaults:<variant>}.
 */
public final class ConstructorFaults implements Runnable {

    /** A class whose initialisation fails. */
    static final class Broken {
        static final int VALUE = init();

        private static int init() {
            throw new IllegalStateException("static init boom");
        }
    }

    /**
     * Creates the fixture, which always fails.
     *
     * @param args the variant
     */
    public ConstructorFaults(String... args) {
        switch (args[0].trim()) {
            case "direct" -> throw new IllegalArgumentException("bad size 0");
            case "nested" -> {
                if (Broken.VALUE == 0) {
                    throw new IllegalStateException("unreachable");
                }
            }
            default -> throw new IllegalArgumentException("unknown variant " + args[0]);
        }
    }

    @Override
    public void run() {
    }
}
