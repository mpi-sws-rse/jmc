package symbolic

abstract class AbstractInteger {
    abstract val value: Int

    abstract fun deepCopy(): AbstractInteger
}