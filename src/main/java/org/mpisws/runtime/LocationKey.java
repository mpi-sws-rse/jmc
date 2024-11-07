package org.mpisws.runtime;

import java.lang.reflect.Field;
import java.util.Objects;

public class LocationKey {

    private final Class<?> clazz;
    private final Object instance;
    private final Field field;
    private final String type;

    public LocationKey(Class<?> clazz, Object instance, Field field, String type) {
        this.clazz = clazz;
        this.instance = instance;
        this.field = field;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof LocationKey other) {
            return this.clazz.equals(other.clazz)
                    && this.instance.equals(other.instance)
                    && this.field.equals(other.field)
                    && this.type.equals(other.type);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(clazz, instance, field, type);
    }
}
