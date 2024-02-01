package org.example.concurrent.programs.kotlin.counter

import kotlin.concurrent.thread

class Counter {
    @Volatile
    var count = 0
}

fun main() {
    val counter = Counter()

    val thread1 = thread(start = true) {

        counter.count++

    }

    val thread2 = thread(start = true) {

        counter.count++

    }

    thread1.join()
    thread2.join()

    println("Final count: ${counter.count}")
}