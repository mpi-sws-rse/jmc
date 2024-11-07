package org.mpisws.checker;

/**
 * The TestTarget class is responsible for storing the details of the program file under test. It
 * maintains several properties including the package, class, method, and path of the program under
 * test. The class provides functionality to retrieve these properties. The class requires the
 * package, class, method, and path of the program under test upon construction. The TestTarget
 * class is designed to encapsulate the details of the program under test and provide an easy way to
 * retrieve these details.
 */
public final class TestTarget {
    /**
     * @property {@link #testPackage} is the package of program under test.
     */
    String testPackage;

    /**
     * @property {@link #testPath} is the path of the program under test.
     */
    String testPath;

    /**
     * @property {@link #testClass} is the class of the program under test.
     */
    String testClass;

    /**
     * @property {@link #testMethod} is the method of the program under test.
     */
    String testMethod;

    /**
     * The following constructor initializes the test package, test class, test method, and test
     * path.
     *
     * @param tPackage is the package of the program under test.
     * @param tClass is the class of the program under test.
     * @param tMethod is the method of the program under test.
     * @param tPath is the path of the program under test.
     */
    public TestTarget(String tPackage, String tClass, String tMethod, String tPath) {
        testPackage = tPackage;
        testClass = tClass;
        testMethod = tMethod;
        testPath = tPath;
    }

    /**
     * Returns the test package.
     *
     * @return the test package.
     */
    public String getTestPackage() {
        return testPackage;
    }

    /**
     * Returns the test class.
     *
     * @return the test class.
     */
    public String getTestClass() {
        return testClass;
    }

    /**
     * Returns the test method.
     *
     * @return the test method.
     */
    public String getTestMethod() {
        return testMethod;
    }

    /**
     * Returns the test path.
     *
     * @return the test path.
     */
    public String getTestPath() {
        return testPath;
    }
}
