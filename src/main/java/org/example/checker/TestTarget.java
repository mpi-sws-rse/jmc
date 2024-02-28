package org.example.checker;

public final class TestTarget {
    String testPackage;
    String testPath;
    String testClass;
    String testMethod;

    public TestTarget(String tPackage, String tClass, String tMethod, String tPath) {
        testPackage = tPackage;
        testClass = tClass;
        testMethod = tMethod;
        testPath = tPath;
    }

    public String getTestPackage() {
        return testPackage;
    }

    public String getTestClass() {
        return testClass;
    }

    public String getTestMethod() {
        return testMethod;
    }

    public String getTestPath() {
        return testPath;
    }
}
