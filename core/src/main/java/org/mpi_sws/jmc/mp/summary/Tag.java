package org.mpi_sws.jmc.mp.summary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The tag under which a record is encoded in a summary: {@code {"t":"<tag>","f":[...]}}. The
 * tag is declared identically in the Java and the Rust program; it is deliberately not the Java
 * class name, so a record without this annotation is refused by {@link MpValueEncoder}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Tag {
    /**
     * The declared tag.
     *
     * @return the tag, as it appears on both sides
     */
    String value();
}
