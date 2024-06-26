package symbolic

import java.io.Serializable

data class SymbolicInteger(
    override val value: Int = 0,
    val name: String = ""
) : Serializable, AbstractInteger() {

    constructor(name: String) : this(0, name)

    // Create a list of SymbolicOperation objects
    var eval: ArithmeticStatement? = null

    fun assign(expression: ArithmeticStatement) {
        eval = expression.deepCopy()
    }

    fun assing(symbolicInteger: SymbolicInteger) {
        if (symbolicInteger.eval != null) {
            eval = symbolicInteger.eval?.deepCopy()
        }
    }

    // write a recursive function that prints the eval of this object. The eval is itself contains SymbolicOperation objects
    fun print() {
        if (eval != null) {
            if (eval?.left is SymbolicInteger) {
                (eval?.left as SymbolicInteger).print()
            } else {
                print(" ${eval?.left?.value} ")
            }
            print(" ${eval?.operator} ")
            if (eval?.right is SymbolicInteger) {
                (eval?.right as SymbolicInteger).print()
            } else {
                print(" ${eval?.right?.value} ")
            }
        } else {
            print(" $name ")
        }
    }

    override fun deepCopy(): SymbolicInteger {
        val copy = SymbolicInteger(value, name)
        if (eval != null) {
            copy.eval = eval?.deepCopy()
        }
        return copy
    }
}
