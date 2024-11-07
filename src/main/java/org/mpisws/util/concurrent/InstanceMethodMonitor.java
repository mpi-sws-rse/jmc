package org.mpisws.util.concurrent;

/**
 * The InstanceMethodMonitor is a MethodMonitor that is associated with a specific object's method.
 */
public class InstanceMethodMonitor extends MethodMonitor {

    /**
     * @property {@link #object} is the object that the method is associated with.
     */
    public Object object;

    public InstanceMethodMonitor(Object object, String methodName, String methodDescriptor) {
        super(methodName, methodDescriptor);
        this.object = object;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }
}
