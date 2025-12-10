package org.example

import java.io.File

import org.example.types.*

val EPSILON = "ε"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No arguments. Nothing to do.")
        return
    }

    val file = File(args.get(0))
    val (candidates, methods) = parse(file)

    println(candidates)
    println(methods)

    val stateVariables = candidates.mapNotNull { getStateVariable(it, methods) }
    println(stateVariables)

    if (stateVariables.isEmpty()) return

    val sv = stateVariables.first()
    
    for (k in 1..4) {
        val fsm = StateMachine(sv.transitions, sv.values, sv.init, listOf(4))
        println("\n$k-tail:")
        println(fsm.tail(k))
    }
}
