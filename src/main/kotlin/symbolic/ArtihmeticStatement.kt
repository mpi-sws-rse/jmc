package symbolic

import java.io.Serializable

class ArithmeticStatement : Serializable {

    var left: AbstractInteger? = null
    var right: AbstractInteger? = null
    var operator: ArithmeticInstructionType? = null

    fun add(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1.deepCopy()
        this.right = var2.deepCopy()
        this.operator = ArithmeticInstructionType.ADD
    }

    fun add(var1: AbstractInteger, var2: Int) {
        this.left = var1.deepCopy()
        this.right = ConcreteInteger(var2)
        this.operator = ArithmeticInstructionType.ADD
    }

    fun add(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2.deepCopy()
        this.operator = ArithmeticInstructionType.ADD
    }

    fun sub(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1
        this.right = var2
        this.operator = ArithmeticInstructionType.SUB
    }

    fun sub(var1: AbstractInteger, var2: Int) {
        this.left = var1
        this.right = ConcreteInteger(var2)
        this.operator = ArithmeticInstructionType.SUB
    }

    fun sub(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2
        this.operator = ArithmeticInstructionType.SUB
    }

    fun mul(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1
        this.right = var2
        this.operator = ArithmeticInstructionType.MUL
    }

    fun mul(var1: AbstractInteger, var2: Int) {
        this.left = var1
        this.right = ConcreteInteger(var2)
        this.operator = ArithmeticInstructionType.MUL
    }

    fun mul(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2
        this.operator = ArithmeticInstructionType.MUL
    }

    fun div(var1: AbstractInteger, var2: AbstractInteger) {
        this.left = var1
        this.right = var2
        this.operator = ArithmeticInstructionType.DIV
    }

    fun div(var1: AbstractInteger, var2: Int) {
        this.left = var1
        this.right = ConcreteInteger(var2)
        this.operator = ArithmeticInstructionType.DIV
    }

    fun div(var1: Int, var2: AbstractInteger) {
        this.left = ConcreteInteger(var1)
        this.right = var2
        this.operator = ArithmeticInstructionType.DIV
    }

    fun deepCopy(): ArithmeticStatement {
        val copy = ArithmeticStatement()
        copy.left = left?.deepCopy()
        copy.right = right?.deepCopy()
        copy.operator = operator
        return copy
    }
}

