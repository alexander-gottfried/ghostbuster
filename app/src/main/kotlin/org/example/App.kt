package org.example

import java.io.File

import org.example.types.*

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
    print(stateVariables)
}
