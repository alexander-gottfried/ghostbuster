package org.example

import java.io.File

import org.example.types.*

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No arguments. Nothing to do.")
        return
    }

    val file = File(args.get(0))
    val compunit = parse(file)

    // TODO do multiple vars later, instead of `firstOrNull`
    val result = findStateVariable(compunit).firstOrNull()
    if (result == null) {
        println("No suitable state variable")
        return
    }

    println(result)
    
    val stateVar = result.name
    result.methods.forEach { (m, c) ->
        println()
        println(m)
        possibleTransitions(stateVar, c)
    }
}
