package programStructure

import java.io.Serializable

data class Monitor(
    @Transient
    var clazz: Class<*>?,
    @Transient
    var instance: Any?
) : Serializable {

    var clazzString: String? = null
    var instanceString: String? = null

    init {
        clazzString = clazz?.name + "@" + clazz?.`package`?.name
        instanceString = instance?.let { "${it}@${it.hashCode().toString(16)}" }
    }

    fun deepCopy(): Monitor {
        return Monitor(
            clazz = copy().clazz,
            instance = copy().instance
        )
    }
}
