package org.mpisws.jmc.strategies.trust;

import org.mpisws.jmc.runtime.RuntimeEvent;

public class Location {
    Object instance;
    String param;

    public Location(Object instance, String param) {
        this.instance = instance;
        this.param = param;
    }

    public static Location fromRuntimeEvent(RuntimeEvent runtimeEvent) {
        Object instance = runtimeEvent.getParam("instance");
        String param = runtimeEvent.getParam("name");
        return new Location(instance, param);
    }

    public static Location fromRuntimeLockEvent(RuntimeEvent runtimeEvent) {
        Object instance = runtimeEvent.getParam("lock");
        String param = runtimeEvent.getParam("owner");
        return new Location(instance, param);
    }

    @Override
    public int hashCode() {
        return (instance.hashCode() + param).hashCode();
    }
}
