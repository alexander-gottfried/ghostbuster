package org.example

import java.io.File

fun parseFSM(file: File): StateMachine? {
    val transitions = mutableListOf<Transition>()
    val inits = mutableListOf<Int>()
    val accepting = mutableListOf<Int>()

    for (line in file.readLines()) {
        val fields = line.split(' ')
        when (fields.size) {
            2 -> {
                val (kind, valueStr) = fields
                val target = when (kind) {
                    "s" -> inits
                    "a" -> accepting
                    else -> {
                        println("not s or a")
                        return null
                    }
                }
                val value = valueStr.toIntOrNull() ?: return null
                target.add(value)
            }
            3 -> {
                val (fromStr, method, toStr) = fields
                val from = fromStr.toIntOrNull() ?: return null
                val to = toStr.toInt() ?: return null
                transitions.add(Transition(from, to, method))
            }
            else -> continue
        }
    }

    val states = mutableSetOf<Int>()
    val methods = mutableSetOf<String>()
    transitions.mapTo(states, Transition::from)
    transitions.mapTo(states, Transition::to)
    transitions.mapTo(methods, Transition::method)

    val init = when (inits.size) {
        0 -> return null
        1 -> inits.first()
        else -> {
            // unique new init
            val newInit = states.map { it - 1 }.min()
            println("STATES: $states")
            println("NEW INIT: $newInit")
            transitions.addAll(
                inits.map { Transition(newInit, it, "ε") }
            )
            newInit
        }
    }

    return StateMachine(transitions, states.toList(), methods.toList(), init, accepting)
}
