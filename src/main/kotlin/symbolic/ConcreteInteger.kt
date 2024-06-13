package symbolic

import java.io.Serializable

data class ConcreteInteger(
    override val value: Int = 0
) : Serializable, AbstractInteger() {

    override fun deepCopy(): ConcreteInteger {
        return ConcreteInteger(value)
    }
}
