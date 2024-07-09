package org.mpisws.util.concurrent;

public class MethodMonitor {

    public Object object;

    public String methodName;

    public String methodDescriptor;

    MethodMonitor(Object object, String methodName, String methodDescriptor) {
        this.object = object;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
    }

    public Object getObject() {
        return object;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDescriptor() {
        return methodDescriptor;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setMethodDescriptor(String methodDescriptor) {
        this.methodDescriptor = methodDescriptor;
    }
}
