package org.example

sealed interface Mu {
    class Zero : Mu
    class One : Mu
    data class Sym(val name: String) : Mu
    data class Union(val lhs: Mu, val rhs: Mu) : Mu
    data class Concat(val lhs: Mu, val rhs: Mu) : Mu
    data class RecVar(val id: Int) : Mu
    data class FixedPoint(val id: Int, val body: Mu) : Mu
}

fun gruppensAlgorithm(machine: StateMachine): Mu {
    val intermediates = machine.toGruppenIntermediates().toMutableMap()
    check(machine.init in intermediates.keys)
    val states = intermediates.keys.minus(machine.init)

    for (state in states) {
        val replaceWithThis = Mu.FixedPoint(
            state,
            intermediates.remove(state)!!
        )
        val newIntermediates = intermediates.map { (id, expr) ->
            Pair(id, expr.withFreeVariableReplaced(state, replaceWithThis))
        }
        // since these overlap 100%, this replaces all keyvals with replaced exprs
        intermediates.plusAssign(newIntermediates)
    }
    val result = Mu.FixedPoint(machine.init, intermediates.get(machine.init)!!)
    return result.withoutUnusedVariables()
}

private fun StateMachine.toGruppenIntermediates(): Map<Int, Mu> {
    val allTransitions = this.transitions.groupBy(Transition::from)
    val intermediates = allTransitions.map { (ruleId, transitions) ->
        var concats = transitions.map(::toMuConcat).reduce(Mu::Union)
        if (ruleId in this.accepting) {
            concats = Mu.Union(Mu.One(), concats)
        }
        Pair(ruleId, concats)
    }.toMap()
    return intermediates
}

private fun toMuConcat(transition: Transition): Mu = when (transition.method) {
    "ε" -> Mu.RecVar(transition.to)
    else -> Mu.Concat(Mu.Sym(transition.method), Mu.RecVar(transition.to))
}

private fun Mu.withFreeVariableReplaced(thisOne: Int, byThis: Mu): Mu = when (this) {
    is Mu.RecVar if this.id == thisOne -> byThis
    // so we don't replace unfree variables:
    is Mu.FixedPoint if this.id != thisOne -> Mu.FixedPoint(
        this.id,
        this.body.withFreeVariableReplaced(thisOne, byThis)
    )
    is Mu.Union -> Mu.Union(
        this.lhs.withFreeVariableReplaced(thisOne, byThis),
        this.rhs.withFreeVariableReplaced(thisOne, byThis)
    )
    is Mu.Concat -> Mu.Concat(
        this.lhs.withFreeVariableReplaced(thisOne, byThis),
        this.rhs.withFreeVariableReplaced(thisOne, byThis)
    )
    else -> this
}

private fun Mu.withoutUnusedVariables(): Mu = when (this) {
    is Mu.FixedPoint if !this.body.varIsFree(this.id) -> this.body.withoutUnusedVariables()
    is Mu.FixedPoint -> Mu.FixedPoint(this.id, this.body.withoutUnusedVariables())
    is Mu.Union -> Mu.Union(
        this.lhs.withoutUnusedVariables(),
        this.rhs.withoutUnusedVariables()
    )
    is Mu.Concat -> Mu.Concat(
        this.lhs.withoutUnusedVariables(),
        this.rhs.withoutUnusedVariables()
    )
    else -> this
}

private fun Mu.varIsFree(thisOnesId: Int): Boolean = when (this) {
    is Mu.RecVar if this.id == thisOnesId -> true
    is Mu.Union -> this.lhs.varIsFree(thisOnesId) || this.rhs.varIsFree(thisOnesId)
    is Mu.Concat -> this.lhs.varIsFree(thisOnesId) || this.rhs.varIsFree(thisOnesId)
    is Mu.FixedPoint if this.id == thisOnesId -> false
    is Mu.FixedPoint -> this.body.varIsFree(thisOnesId)
    else -> false
}
