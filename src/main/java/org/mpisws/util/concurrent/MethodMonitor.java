package org.mpisws.util.concurrent;

/** The MethodMonitor is an abstract class that is used to monitor a method. */
public abstract class MethodMonitor {

  /**
   * @property {@link #methodName} is the name of the method.
   */
  public String methodName;

  /**
   * @property {@link #methodDescriptor} is the descriptor of the method.
   */
  public String methodDescriptor;

  MethodMonitor(String methodName, String methodDescriptor) {
    this.methodName = methodName;
    this.methodDescriptor = methodDescriptor;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getMethodDescriptor() {
    return methodDescriptor;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public void setMethodDescriptor(String methodDescriptor) {
    this.methodDescriptor = methodDescriptor;
  }
}
