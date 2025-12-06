package org.example

import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No arguments. Nothing to do.")
        return
    }

    val file = File(args.get(0))
    val compunit = parse(file)

    findStateVariable(compunit)
}
