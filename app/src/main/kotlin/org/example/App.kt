package org.example

import java.io.File

import org.example.types.*

val EPSILON = "ε"

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No arguments. Nothing to do.")
        return
    }

    val (filekind, file, doAllStates) = parseCliOptions(args) ?: return
    val machine = when (filekind) {
        FileKind.JAVA -> {
            val (candidates, methods) = parse(file)
            val stateVariables = candidates.mapNotNull { getStateVariable(it, methods) }
            if (stateVariables.isEmpty()) {
                println("No FSM state variable found")
                return
            }
            val (_, machine) = stateVariables.first()
            machine
        }
        FileKind.FSM -> {
            val machine = parseFSM(file) ?: return
            machine
        }
    }

    if (doAllStates) {
        for (state in machine.values) {
            val gs = machine.withNewAccepting(state)
            val mureg = gruppensAlgorithm(gs)
            val simplified = mureg.rewriteRule().withoutUnusedVariables()
            println("\nμ-regex for G($state):")
            println(simplified.asLatex())

            val cat = simplified.toCat()
            println("\ntrace formula for G($state):")
            println(cat.asLatex())
        }
    } else {
        check(machine.accepting.isNotEmpty())
        val mureg = gruppensAlgorithm(machine)
        val simplified = mureg.rewriteRule().withoutUnusedVariables()
        println("As μ-regex:")
        println(simplified.asLatex())
        val cat = simplified.toCat()
        println("\nAs Trace Formula:")
        println(cat.asLatex())
    }
}

private enum class FileKind {
    JAVA, FSM,
}

private data class Options(
    val filekind: FileKind,
    val file: File,
    val doAllStates: Boolean,
)

private enum class CliState {
    TAKE_ANY, TAKE_FSM
}

private fun parseCliOptions(args: Array<String>): Options? {
    var state = CliState.TAKE_ANY
    var filekind: FileKind? = null
    var file: File? = null
    var doAllStates = false
    for (arg in args) {
        when (state) {
            CliState.TAKE_ANY -> when (arg) {
                "-fsm" -> state = CliState.TAKE_FSM
                "-all" -> doAllStates = true
                else -> {
                    filekind = FileKind.JAVA
                    file = File(arg)
                    doAllStates = true
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
    return Options(filekind, file, doAllStates)
}
