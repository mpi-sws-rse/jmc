package programStructure

import java.io.Serializable

/**
 * Monitor class is used to store the monitor object that was entered.
 */
data class Monitor(

    /**
     * @property clazz The class of the monitor
     * <p>
     * This field is transient and is not serialized
     */
    @Transient
    var clazz: Class<*>?,

    /**
     * @property instance The instance of the monitor
     * <p>
     * This field is transient and is not serialized
     */
    @Transient
    var instance: Any?
) : Serializable {

    /**
     * @property clazzString The class of the monitor as a string
     * <p>
     * This field is used to avoid serialization issues when the class is serialized and deserialized.
     */
    var clazzString: String? = clazz?.name + "@" + clazz?.`package`?.name

    /**
     * @property instanceString The instance of the monitor as a string
     * <p>
     * This field is used to avoid serialization issues when the instance is serialized and deserialized.
     */
    var instanceString: String? = instance?.let { "${it}@${it.hashCode().toString(16)}" }

    /**
     * Returns a deep copy of this object
     */
    fun deepCopy(): Monitor {
        return Monitor(
            clazz = copy().clazz,
            instance = copy().instance
        )
    }
}