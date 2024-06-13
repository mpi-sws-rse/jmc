package symbolic

import org.sosy_lab.common.ShutdownManager
import org.sosy_lab.common.configuration.Configuration
import org.sosy_lab.common.log.BasicLogManager
import org.sosy_lab.common.log.LogManager
import org.sosy_lab.java_smt.SolverContextFactory
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.Model
import org.sosy_lab.java_smt.api.NumeralFormula
import org.sosy_lab.java_smt.api.ProverEnvironment
import org.sosy_lab.java_smt.api.SolverContext
import java.io.Serializable
import kotlin.system.exitProcess

class ArithmeticFormula : Serializable {


    var config: Configuration = Configuration.builder().build()


    var logger: LogManager = BasicLogManager.create(config)


    var shutdown: ShutdownManager = ShutdownManager.create()


    var integerVariableMap: MutableMap<String, NumeralFormula.IntegerFormula> = mutableMapOf()


    var context: SolverContext? = SolverContextFactory.createSolverContext(
        config, logger, shutdown.notifier, SolverContextFactory.Solvers.SMTINTERPOL
    )


    val fmgr = context!!.formulaManager


    val bmgr = fmgr.booleanFormulaManager


    val imgr = fmgr.integerFormulaManager

    var model: Model? = null

    var prover: ProverEnvironment = context!!.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)

    fun eq(var1: AbstractInteger, var2: AbstractInteger): Boolean {

        if (var1 is SymbolicInteger && var2 is SymbolicInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var1)
            val rightOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var2)

            val formula = imgr.equal(leftOperand, rightOperand)
            println(formula)
            return solver(formula)
        } else if (var1 is SymbolicInteger && var2 is ConcreteInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var1)
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.value.toLong())

            val formula = imgr.equal(leftOperand, rightOperand)
            println(formula)
            return solver(formula)
        } else if (var1 is ConcreteInteger && var2 is SymbolicInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var1.value.toLong())
            val rightOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var2)

            val formula = imgr.equal(leftOperand, rightOperand)
            println(formula)
            return solver(formula)
        } else if (var1 is ConcreteInteger && var2 is ConcreteInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var1.value.toLong())
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.value.toLong())

            val formula = imgr.equal(leftOperand, rightOperand)
            println(formula)
            return solver(formula)
        } else {
            println("[Symbolic Execution] Unsupported type")
            exitProcess(0)
        }
    }

    private fun makeIntegerFormula(symbolicInteger: SymbolicInteger): NumeralFormula.IntegerFormula {
        if (symbolicInteger.eval == null) {
            if (integerVariableMap.containsKey(symbolicInteger.name)) {
                return integerVariableMap[symbolicInteger.name]!!
            } else {
                val variable = imgr.makeVariable(symbolicInteger.name)
                integerVariableMap[symbolicInteger.name] = variable
                return variable
            }
        }
        var leftOperand: NumeralFormula.IntegerFormula = if (symbolicInteger.eval?.left is SymbolicInteger) {
            makeIntegerFormula(symbolicInteger.eval?.left as SymbolicInteger)
        } else {
            imgr.makeNumber(symbolicInteger.eval?.left?.value!! as Long)
        }
        var rightOperand: NumeralFormula.IntegerFormula = if (symbolicInteger.eval?.right is SymbolicInteger) {
            makeIntegerFormula(symbolicInteger.eval?.right as SymbolicInteger)
        } else {
            imgr.makeNumber(symbolicInteger.eval?.right?.value!!.toLong())
        }

        return when (symbolicInteger.eval?.operator) {
            ArithmeticInstructionType.ADD -> {
                imgr.add(leftOperand, rightOperand)
            }

            ArithmeticInstructionType.SUB -> {
                imgr.subtract(leftOperand, rightOperand)
            }

            ArithmeticInstructionType.MUL -> {
                println(leftOperand)
                println(rightOperand)
                imgr.multiply(leftOperand, rightOperand)
            }

            ArithmeticInstructionType.DIV -> {
                imgr.divide(leftOperand, rightOperand)
            }

            else -> {
                println("[Symbolic Execution] Unsupported operator [${symbolicInteger.eval?.operator}]")
                exitProcess(0)
            }
        }
    }

    private fun solver(formula: BooleanFormula): Boolean {
        prover.addConstraint(formula)
        val isUnsat = prover.isUnsat
        if (!isUnsat) {
            model = prover.model
            evaluate()
            return true
        } else {
            evaluate()
            return false
        }
    }

    private fun evaluate() {
        // for all variables in integerVariableMap call the model.evaluate() method
        // and print the value of the variable
        for (variable in integerVariableMap) {
            val value = model!!.evaluate(variable.value)
            println("Variable: ${variable.key} Value: $value")
        }
    }

    fun eq(var1: AbstractInteger, var2: Int): Boolean {
        if (var1 is SymbolicInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var1)
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.toLong())

            val formula = imgr.equal(leftOperand, rightOperand)
            println(formula)
            return solver(formula)
        } else if (var1 is ConcreteInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var1.value.toLong())
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.toLong())

            val formula = imgr.equal(leftOperand, rightOperand)
            println(formula)
            return solver(formula)
        } else {
            println("[Symbolic Execution] Unsupported type")
            exitProcess(0)
        }
    }

    fun eq(var1: Int, var2: AbstractInteger): Boolean {
        return eq(var2, var1)
    }

    fun neq(var1: AbstractInteger, var2: AbstractInteger): Boolean {
        if (var1 is SymbolicInteger && var2 is SymbolicInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var1)
            val rightOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var2)

            val formula = bmgr.not(imgr.equal(leftOperand, rightOperand))
            println(formula)
            return solver(formula)
        } else if (var1 is SymbolicInteger && var2 is ConcreteInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var1)
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.value.toLong())
            val formula = bmgr.not(imgr.equal(leftOperand, rightOperand))
            println(formula)
            return solver(formula)
        } else if (var1 is ConcreteInteger && var2 is SymbolicInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var1.value.toLong())
            val rightOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var2)

            val formula = bmgr.not(imgr.equal(leftOperand, rightOperand))
            println(formula)
            return solver(formula)
        } else if (var1 is ConcreteInteger && var2 is ConcreteInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var1.value.toLong())
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.value.toLong())

            val formula = bmgr.not(imgr.equal(leftOperand, rightOperand))
            println(formula)
            return solver(formula)
        } else {
            println("[Symbolic Execution] Unsupported type")
            exitProcess(0)
        }
    }

    fun neq(var1: AbstractInteger, var2: Int): Boolean {
        if (var1 is SymbolicInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = makeIntegerFormula(var1)
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.toLong())

            val formula = bmgr.not(imgr.equal(leftOperand, rightOperand))
            println(formula)
            return solver(formula)
        } else if (var1 is ConcreteInteger) {
            val leftOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var1.value.toLong())
            val rightOperand: NumeralFormula.IntegerFormula = imgr.makeNumber(var2.toLong())

            val formula = bmgr.not(imgr.equal(leftOperand, rightOperand))
            println(formula)
            return solver(formula)
        } else {
            println("[Symbolic Execution] Unsupported type")
            exitProcess(0)
        }
    }

    fun neq(var1: Int, var2: AbstractInteger): Boolean {
        return neq(var2, var1)
    }
}