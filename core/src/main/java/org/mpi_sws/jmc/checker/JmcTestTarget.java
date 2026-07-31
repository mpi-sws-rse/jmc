package org.mpi_sws.jmc.checker;

/**
 * The program under test, as seen by the model checker.
 *
 * <p>This abstraction decouples {@link JmcModelChecker} from <em>how</em> a test is run: the checker
 * only needs a name and a way to run the program once. The checker calls {@link #invoke()} once per
 * iteration. The standard implementation is {@link JmcFunctionalTestTarget}, which the JUnit
 * integration fills with a reflective call to the annotated test method.
 */
public interface JmcTestTarget {

    /**
     * Returns the display name of the target (e.g. the test method name).
     *
     * @return the target name
     */
    String name();

    /** Runs the program under test once. Called by the checker on each iteration. */
    void invoke();
}
