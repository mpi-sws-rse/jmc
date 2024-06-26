package programStructure

import java.io.Serializable
import java.lang.reflect.Field

/**
 * Location class  is used to store the location of a field in a class.
 */
data class Location(

    /**
     * @property clazz The class of the field
     * <p>
     * This field is transient and is not serialized
     */
    @Transient
    var clazz: Class<*>?,

    /**
     * @property instance The instance of the class
     * <p>
     * This field is transient and is not serialized
     */
    @Transient
    var instance: Any?,

    /**
     * @property field The field
     * <p>
     * This field is transient and is not serialized
     */
    @Transient
    var field: Field?,

    /**
     * @property value The value of the field
     * <p>
     * This field is transient and is not serialized
     */
    @Transient
    var value: Any?,

    /**
     * @property type The type of the field
     */
    var type: String?
) : Serializable {

    /**
     * @property clazzString The class of the field as a string
     * <p>
     * This field is used to avoid serialization issues when the class is serialized and deserialized.
     */
    var clazzString: String? = clazz?.name + "@" + clazz?.`package`?.name

    /**
     * @property instanceString The instance of the class as a string
     * <p>
     * This field is used to avoid serialization issues when the instance is serialized and deserialized.
     */
    var instanceString: String? = instance?.let { "${it}@${it.hashCode().toString(16)}" }

    /**
     * @property fieldString The field as a string
     * <p>
     * This field is used to avoid serialization issues when the field is serialized and deserialized.
     */
    var fieldString: String? = field?.let { "${it.name}@${it.hashCode().toString(16)}" }

    /**
     * @property valueString The value of the field as a string
     * <p>
     * This field is used to avoid serialization issues when the value is serialized and deserialized.
     */
    var valueString: String? = value?.let { "${it}@${it.hashCode().toString(16)}" }

    /**
     * Returns a deep copy of this object
     *
     * @return a deep copy of this object
     */
    fun deepCopy(): Location {
        return Location(
            clazz = copy().clazz,
            instance = copy().instance,
            field = copy().field,
            value = copy().value,
            type = copy().type
        )
    }

    /**
     * Checks if the field is a primitive type or not.
     *
     * The primitive types are: int(I), long(J), float(F), double(D), char(S), byte(B), short(C), boolean(Z), symbolic int(SI),
     * symbolic boolean(SZ)
     *
     * @return true if the field is a primitive type, false otherwise
     */
    fun isPrimitive(): Boolean {
        return when (type) {
            "I",  // int(I)
            "Z",  // boolean(Z)
            "SZ", // symbolic boolean(SZ)
            "F",  // float(F)
            "D",  // double(D)
            "S",  // char(S)
            "J",  // long(J)
            "C",  // short(C)
            "B",  // byte(B)
            "SI" -> true // symbolic int(SI)
            else -> false
        }
    }
}