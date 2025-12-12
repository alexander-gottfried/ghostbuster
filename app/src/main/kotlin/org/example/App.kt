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

    val (_, machine) = stateVariables.first()

    val mureg = gruppensAlgorithm(machine.withNewAccepting(2))
    println(mureg)
    
    /*
    for (s in machine.values) {
        val fsm = machine.withNewAccepting(listOf(s))
        println(fsm.tail(1))
    }
    val tails = machine.values.map {
        machine.withNewAccepting(listOf(it)).tail(1)//.subsetConstruction()
    }
    val p = tails.joinToString("\n\n")
    println("\ntails: $p")
    val overlappers = indecesOfOverlappers(tails)
    println(overlappers)
    */
}
