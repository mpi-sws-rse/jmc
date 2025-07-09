package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.JmcRuntimeEvent;

public class Location {
    Object instance;
    String param;

    public Location(Object instance, String param) {
        this.instance = instance;
        this.param = param;
    }

    public static Location fromRuntimeEvent(JmcRuntimeEvent runtimeEvent) {
        Object instance = runtimeEvent.getParam("instance");
        if (instance == null) {
            // This is because the call is a static method call
            instance = runtimeEvent.getParam("owner");
        }
        String param = runtimeEvent.getParam("name");
        return new Location(instance, param);
    }

    @Override
    public int hashCode() {
        return (instance.hashCode() + param).hashCode();
    }
}
