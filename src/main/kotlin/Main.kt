import dpor.Trust
import parser.Tokenizer

/*
 * TODO(): Write an intro for the reader
 */

fun main() {

    // Creating a tokenizer object
    val tknzr = Tokenizer()

    /*
     If you want to see an "invalid input" result, uncomment the following line

        tknzr.readFile("src/main/resources/Input_Program/Program_Test_1")
    */

    /*
     If you want to run the program under a correct input,
        uncomment the following line.
     In this part, the interpreter will analyze the program.
     It captures all the write and read events from each tread.
     */

    tknzr.readFile("src/main/resources/Input_Program/Program_Test_6")

    /*
     For testing different programs use these :
    */
    //tknzr.readFile("src/main/resources/Input_Program/Program_Test_2")
    // tknzr.readFile("src/main/resources/Input_Program/Program_Test_3")

    /*
     To check if the tokenizer works fine, uncomment the following

        if (fr.getThreadsInfo() == null){
            println("BUG!!")
        }
     */

    // Then, it will give these events to the trust algorithm
    val trust = Trust()
    trust.setThreads(tknzr.getThreadsInfo())


    /*
    To Check the consistency for integration between
        Trust and Tokenizer uncomment the following.

    if (trust.allJMCThread != null){
        println("---------------------------")
        for (t in fr.threads){
            println(t)
            for (e in t.value.instructions){
                println(e)
                if (e.type == EventType.READ){
                    val r : ReadEvent = e as ReadEvent
                    println("(${r.loc} , ${r.value})")
                }
                if (e.type== EventType.WRITE){
                    val w : WriteEvent = e as WriteEvent
                    println("(${w.loc} , ${w.value})")
                }
            }
        }
    }
    */

    // Here the Trust algorithm will be run
    if (trust.allJMCThread != null){
        println("Parsing has been completed")
        trust.verify()
        println("All the possible execution graphs have been visited.")
    } else {
        println("Invalid input")
    }
}