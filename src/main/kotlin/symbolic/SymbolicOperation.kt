package symbolic

import java.io.Serializable

data class SymbolicOperation(
    val leftOperand: AbstractInteger,
    val rightOerand: AbstractInteger,
    val operator: ComparisonInstructionType
) : Serializable