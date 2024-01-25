package parser

import programStructure.*
import java.io.File

// TODO() : This Tokenizer accepts only global variables (I will extend it with local variables)
// TODO() : Checking var or val for local variables should be considered in future
// TODO() : This Tokenizer assumes type safety (I will extend it with type checker)
// TODO() : This Tokenizer only supports Integer types (I will extend it with other types)
// TODO() : This Tokenizer only supports singleton logical comparison (I will extend it with compound logical comparison)


class Tokenizer (private var threadCounter : Int = 0, private var serialNumber : Int = 0){
    private var errorHappened = false
    private var threads = mutableMapOf<Int, Threads>()
    fun readFile(fileName: String){
        File(fileName).forEachLine {
            when {
                it.first() == '$' -> newThread()
                it.contains("""\b\s*(=)\s*\b""".toRegex()) -> newAssignment(it)
                it.contains("""\b\s*(==|!=|<=|>=|<|>)\s*\b""".toRegex()) -> newLogicalComparison(it)
                // TODO() : add unary '!' operation support
                // TODO() : go on :))
            }
        }
    }
    private fun newThread() {
        threadCounter += 1
        val trd = Threads(threadCounter)
        threads[threadCounter] = trd
        serialNumber = 0
        //println("Thread Number : "+ this.threadCounter) for debugging
        //println(command) for debugging
    }

    private fun newAssignment(command : String){

        var isLHSConsistent = false
        var isRHSConsistent = false
        var isReadHappened = true
        var wr : WriteEvent? = null
        var rd : ReadEvent? = null

        // At first, we need to check that the left hand-side of the assignment is a variable
        // If it is consistent, then we need to make a write event object

        val leftHandSide = isThisSideConsistent("""^(\w+|\w+\.?\w+)\s*=""",command,1)
        //println("Left-hand side: $leftHandSide")  for debugging
        if (leftHandSide != "empty"){
            val wrloc = isVariable(leftHandSide)
            if(wrloc != null){
                //println("Left-hand side: $leftHandSide") for debugging
                isLHSConsistent = true
                wr = WriteEvent(threadCounter)
                wr.loc = wrloc

            }
        }

        // Next, we need to check that the right hand-side of the assignment is a variable or value
        if (isLHSConsistent){
            val rightHandSide = isThisSideConsistent("""=\s*(\w+|\w+\.?\w+)""",command,1)
            //println("Right-hand side: $rightHandSide") for debugging
            if (rightHandSide != "empty"){
                val rdloc = isVariable(rightHandSide)
                if(rdloc != null){
                    //println("Right-hand side: $rightHandSide") for debugging
                    isRHSConsistent = true
                    rd = ReadEvent(threadCounter)
                    rd.loc = rdloc
                }else{
                    val numVal = isInteger(rightHandSide)
                    if(numVal != "" ){
                        //println("Right-hand side: $rightHandSide") for debugging
                        isReadHappened = false
                        isRHSConsistent = true
                        if (wr != null) { // The flow logic of my code indicates that if we are in this line, it means that wr has been created, but the compiler does not give the permission fo direct assignment
                            wr.value = numVal
                        }
                    }
                }

            }

        } else{
            errorHappened = true
        }
        // Finally, we need to combine the results
        if (isRHSConsistent){
            if(isReadHappened){
                if (rd != null) { // The flow logic of my code indicates that if we are in this line, it means that wr has been created, but the compiler does not give the permission fo direct assignment
                    serialNumber += 1
                    rd.serial = serialNumber
                    threads[rd.tid]?.instructions?.add(rd)
                }
            }
            serialNumber += 1
            if (wr != null) { // The flow logic of my code indicates that if we are in this line, it means that wr has been created, but the compiler does not give the permission fo direct assignment
                wr.serial = serialNumber
                threads[wr.tid]?.instructions?.add(wr)
            }
            //println(this.serialNumber.toString()+" -- "+command) for debugging
        } else {
            errorHappened = true
            notValidStatement(command)
        }
    }

    private fun newLogicalComparison(command : String){
        var isLHSConsistent = false
        var isRHSConsistent = false
        var isReadHappened1 = true
        var isReadHappened2 = true
        var rd1 : ReadEvent? = null
        var rd2 : ReadEvent? = null

        // At first, we need to check that the left hand-side of the assignment is a variable
        // If it was not a variable, then we check that it is a value
        val leftHandSide = isThisSideConsistent("""(\w+|\w+\.?\w+)\s*(==|!=|<=|>=|<|>)""",command,1)
        //println("Left-hand side: $leftHandSide") for debugging
        if(leftHandSide != "empty"){
            val rdloc = isVariable(leftHandSide)
            if(rdloc != null){
                //println("Left-hand side: $leftHandSide") for debugging
                isLHSConsistent = true
                rd1 = ReadEvent(threadCounter)
                rd1.loc = rdloc
            }else{
                val numVal = isInteger(leftHandSide)
                if(numVal != "" ){
                    //println("Left-hand side: $leftHandSide") for debugging
                    isReadHappened1 = false
                    isLHSConsistent = true
                }
            }
        }
        // Next, we need to check that the right hand-side of the comparison is a variable or value
        if (isLHSConsistent) {
            val rightHandSide = isThisSideConsistent("""(==|!=|<=|>=|<|>)\s*(\w+|\w+\.?\w+)""", command,2)
            //println("Right-hand side: $rightHandSide") for debugging
            if (rightHandSide != "empty") {
                val rdloc = isVariable(rightHandSide)
                if (rdloc != null) {
                    //println("Right-hand side: $rightHandSide") for debugging
                    isRHSConsistent = true
                    rd2 = ReadEvent(threadCounter)
                    rd2.loc = rdloc
                } else {
                    val numVal = isInteger(rightHandSide)
                    if (numVal != "") {
                        //println("Right-hand side: $rightHandSide") for debugging
                        isReadHappened2 = false
                        isRHSConsistent = true
                    }
                }

            }
            // Finally, we need to combine the results
            if (isRHSConsistent) {
                if (isReadHappened1) {
                    if (rd1 != null) { // The flow logic of my code indicates that if we are in this line, it means that wr has been created, but the compiler does not give the permission fo direct assignment
                        serialNumber += 1
                        rd1.serial = serialNumber
                        threads[rd1.tid]?.instructions?.add(rd1)
                    }
                }
                if (isReadHappened2) {
                    if (rd2 != null) { // The flow logic of my code indicates that if we are in this line, it means that wr has been created, but the compiler does not give the permission fo direct assignment
                        serialNumber += 1
                        rd2.serial = serialNumber
                        threads[rd2.tid]?.instructions?.add(rd2)
                    }
                }
                // println(this.serialNumber.toString()+" -- "+command) for debugging
            } else {
                errorHappened = true
                notValidStatement(command)
            }
        }
    }

    private fun isVariable(word : String) : Location?{
        // Checking the type of variable (Class/Instance)
        val loc : Location?
        if (!Regex("""\.""").containsMatchIn(word)){
            // Consistency checking of class variable
            return if(Regex("""[a-zA-Z_]""").matches(word.first().toString())){
                loc = Location(null,word)
                loc
            } else null
        }else{
            // Consistency checking of instance variable
            val variable = word.substringAfter(".", "")
            val obj = word.substringBefore(".","")
            return if(variable == "" || obj == "") null
            else{
                if(!Regex("""[a-zA-Z_]""").matches(variable.first().toString()) ||
                    !Regex("""[a-zA-Z_]""").matches(obj.first().toString())) null
                else{
                    loc = Location(obj,variable)
                    loc
                }
            }
        }
    }

    private fun isInteger(word : String) : String{
        // Checking Integer semantics
        return if(Regex("""^(\d)+$""").matches(word)) word
        else ""
    }

    private fun isThisSideConsistent(pattern : String, expression : String, index : Int) : String{
        val matchResult = Regex(pattern).find(expression)
        return matchResult?.groupValues?.get(index)?: "empty"
    }

    private fun notValidStatement(command : String){
        println("Dude! this statement is not valid in Kotlin : $command")
    }
    fun getThreadsInfo() : MutableMap<Int, Threads>? {
        return if(errorHappened) null
               else this.threads
    }
}