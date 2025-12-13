package org.example

import java.io.File

import org.example.types.*

val EPSILON = "ε"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No arguments. Nothing to do.")
        return
    }

    val (filekind, file) = parseCliOptions(args) ?: return
    val machine = when (filekind) {
        FileKind.JAVA -> {
            val (candidates, methods) = parse(file)
            val stateVariables = candidates.mapNotNull { getStateVariable(it, methods) }
            if (stateVariables.isEmpty()) return
            val (_, machine) = stateVariables.first()
            machine
        }
        FileKind.FSM -> {
            val machine = parseFSM(file) ?: return
            machine
        }
    }

    val mureg = gruppensAlgorithm(machine.withNewAccepting(2))
    println()
    println(mureg.asString())
}

private enum class FileKind {
    JAVA, FSM,
}

private data class Options(val filekind: FileKind, val file: File)

private enum class CliState {
    TAKE_ANY, TAKE_FSM,
}

private fun parseCliOptions(args: Array<String>): Options? {
    var state = CliState.TAKE_ANY
    var filekind: FileKind? = null
    var file: File? = null
    for (arg in args) {
        when (state) {
            CliState.TAKE_ANY -> when (arg) {
                "-fsm" -> state = CliState.TAKE_FSM
                else -> {
                    filekind = FileKind.JAVA
                    file = File(arg)
                    break
                }
            }
            CliState.TAKE_FSM -> {
                filekind = FileKind.FSM
                file = File(arg)
                break
            }
        }
    }
    if (filekind == null || file == null) return null
    return Options(filekind, file)
}
