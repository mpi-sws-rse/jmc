package org.mpisws.util.concurrent;

/** The StaticMethodMonitor is a MethodMonitor that is associated with a specific class's method. */
public class StaticMethodMonitor extends MethodMonitor {

  /**
   * @property {@link #className} is the class that the method is associated with.
   */
  public String className;

  public StaticMethodMonitor(String className, String methodName, String methodDescriptor) {
    super(methodName, methodDescriptor);
    this.className = className;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }
}
