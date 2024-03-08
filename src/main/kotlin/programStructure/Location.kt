package programStructure

import java.io.Serializable
import java.lang.reflect.Field

/**
 * Location class
 * This class is used to store the location of a field in a class
 * @property clazz The class of the field
 * @property instance The instance of the class
 * @property field The field
 * @property value The value of the field
 * @property type The type of the field
 */
data class Location(
    /**
     * The following fields are transient because they are not serializable.
     */
    @Transient
    var clazz: Class<*>?,
    @Transient
    var instance: Any?,
    @Transient
    var field: Field?,
    @Transient
    var value: Any?,
    var type: String?
): Serializable {

    /**
     * The following fields are used to store the string representation
     * of the class, instance, field and value. This is used to avoid
     * serialization issues when the class is serialized and deserialized.
     */
    var clazzString: String? = null
    var instanceString: String? = null
    var fieldString: String? = null
    var valueString: String? = null

    init {
        clazzString = clazz?.name + "@" + clazz?.`package`?.name
        instanceString = instance?.let { "${it}@${it.hashCode().toString(16)}" }
        fieldString = field?.let { "${it.name}@${it.hashCode().toString(16)}" }
        valueString = value?.let { "${it}@${it.hashCode().toString(16)}" }
    }

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
     * This method is used check if the field is a primitive type
     * @return true if the field is a primitive type, false otherwise
     */
    fun isPrimitive(): Boolean {
        return when(type){
            // int
            "I" -> true
            // long
            "Z" -> true
            // float
            "F" -> true
            // double
            "D" -> true
            // char
            "S" -> true
            // byte
            "J" -> true
            // short
            "C" -> true
            // boolean
            "B" -> true
            else -> false
        }
    }
}