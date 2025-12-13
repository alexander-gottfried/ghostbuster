package org.example

sealed interface Mu {
    class Zero : Mu
    class One : Mu
    data class Sym(val name: String) : Mu
    data class Union(val lhs: Mu, val rhs: Mu) : Mu
    data class Concat(val lhs: Mu, val rhs: Mu) : Mu
    data class RecVar(val id: Int) : Mu
    data class FixedPoint(val id: Int, val body: Mu) : Mu
    data class Star(val expr: Mu) : Mu

    fun isAtom(): Boolean = (
        this is Zero ||
        this is One ||
        this is Sym ||
        this is RecVar
    )

    companion object {
        fun mkZero() = Mu.Zero()
        fun mkOne() = Mu.One()
        fun mkSym(name: String) = Mu.Sym(name)
        fun mkRecVar(id: Int) = Mu.RecVar(id)

        fun mkUnion(lhs: Mu, rhs: Mu) = when (lhs) {
            is Mu.Zero -> rhs
            else -> Mu.Union(lhs, rhs)
        }

        fun mkConcat(lhs: Mu, rhs: Mu): Mu {
            if (lhs is One) return rhs
            if (rhs is One) return lhs
            if (lhs is Mu.Zero || rhs is Mu.Zero) {
                return Mu.Zero()
            }
            return Mu.Concat(lhs, rhs)
        }

        fun mkFixedPoint(id: Int, body: Mu) = if (body.isAtom() && body !is Mu.RecVar) {
            body 
        } else {
            Mu.FixedPoint(id, body)
        }

        fun mkStar(expr: Mu) = when (expr) {
            is Mu.Star -> expr
            else -> Mu.Star(expr)
        }
    }
}

fun gruppensAlgorithm(machine: StateMachine): Mu {
    val intermediates = machine.toGruppenIntermediates().toMutableMap()
    check(machine.init in intermediates.keys)
    val states = intermediates.keys.minus(machine.init)

    println("\n0:")
    intermediates.forEach { (s, e) ->
        println("$s -> ${e.asString()}")
    }

    for (state in states) {
        val replaceWithThis = Mu.mkFixedPoint(
            state,
            intermediates.remove(state)!!
        )
        val newIntermediates = intermediates.map { (id, expr) ->
            Pair(id, expr.withFreeVariableReplaced(state, replaceWithThis))
        }
        // since these overlap 100%, this replaces all keyvals with replaced exprs
        intermediates.plusAssign(newIntermediates)

        println("\n$state:")
        intermediates.forEach { (s, e) ->
            println("$s -> ${e.asString()}")
        }
    }
    val result = Mu.mkFixedPoint(machine.init, intermediates.get(machine.init)!!)
    return result.rewriteRule().withoutUnusedVariables()
}


private fun StateMachine.toGruppenIntermediates(): Map<Int, Mu> {
    val allTransitions = this.transitions.groupBy(Transition::from)
    val intermediates = allTransitions.map { (ruleId, transitions) ->
        // makes it so looping transitions come first
        val sortedTransitions = transitions.sortedBy { (_, to, _) ->
            if (to == ruleId) 0 else 1
        }
        var concats = sortedTransitions.map(::toMuConcat).reduce(Mu::Union)
        if (ruleId in this.accepting) {
            concats = Mu.mkUnion(Mu.One(), concats)
        }
        Pair(ruleId, concats)
    }.toMap().toMutableMap()
    // cover edge cases with sink states
    for (state in this.values.filter { !intermediates.containsKey(it) }) {
        intermediates.put(state, Mu.mkOne())
    }
    return intermediates
}

private fun toMuConcat(transition: Transition): Mu = when (transition.method) {
    "ε" -> Mu.mkRecVar(transition.to)
    else -> Mu.mkConcat(Mu.Sym(transition.method), Mu.mkRecVar(transition.to))
}

private fun Mu.withFreeVariableReplaced(thisOne: Int, byThis: Mu): Mu = when (this) {
    is Mu.RecVar if this.id == thisOne -> byThis
    // so we don't replace unfree variables:
    is Mu.FixedPoint if this.id != thisOne -> Mu.mkFixedPoint(
        this.id,
        this.body.withFreeVariableReplaced(thisOne, byThis)
    )
    is Mu.Union -> Mu.mkUnion(
        this.lhs.withFreeVariableReplaced(thisOne, byThis),
        this.rhs.withFreeVariableReplaced(thisOne, byThis)
    )
    is Mu.Concat -> Mu.mkConcat(
        this.lhs.withFreeVariableReplaced(thisOne, byThis),
        this.rhs.withFreeVariableReplaced(thisOne, byThis)
    )
    else -> this
}

private fun Mu.withoutUnusedVariables(): Mu = when (this) {
    is Mu.FixedPoint if !this.body.varIsFree(this.id) -> this.body.withoutUnusedVariables()
    is Mu.FixedPoint -> Mu.mkFixedPoint(this.id, this.body.withoutUnusedVariables())
    else -> this.passOnTransform(Mu::withoutUnusedVariables)
}

private fun Mu.varIsFree(thisOnesId: Int): Boolean = when (this) {
    is Mu.RecVar if this.id == thisOnesId -> true
    is Mu.Union -> this.lhs.varIsFree(thisOnesId) || this.rhs.varIsFree(thisOnesId)
    is Mu.Concat -> this.lhs.varIsFree(thisOnesId) || this.rhs.varIsFree(thisOnesId)
    is Mu.FixedPoint if this.id == thisOnesId -> false
    is Mu.FixedPoint -> this.body.varIsFree(thisOnesId)
    else -> false
}

fun Mu.asString(): String = when (this) {
    is Mu.Zero -> "0"
    is Mu.One -> "1"
    is Mu.Sym -> this.name
    is Mu.RecVar -> "X_${this.id}"
    is Mu.Union -> "${this.lhs.asString()} + ${this.rhs.asString()}"
    is Mu.Concat -> {
        fun paren(m: Mu): String = if (m is Mu.Union) "(${m.asString()})" else m.asString()
        "${paren(this.lhs)} ${paren(this.rhs)}"
    }
    is Mu.FixedPoint -> "(μX_${this.id}.${this.body.asString()})"
    is Mu.Star -> if (this.expr.isAtom()) "${this.expr.asString()}*" else "(${this.expr.asString()})*"
}

fun Mu.asLatex(): String = when (this) {
    is Mu.Zero -> "0"
    is Mu.One -> "1"
    is Mu.Sym -> "\\idm{${this.name}}"
    is Mu.RecVar -> "X_${this.id}"
    is Mu.Union -> "${this.lhs.asString()} + ${this.rhs.asString()}"
    is Mu.Concat -> {
        fun paren(m: Mu): String = if (m is Mu.Union) "(${m.asString()})" else m.asString()
        "${paren(this.lhs)} ${paren(this.rhs)}"
    }
    is Mu.FixedPoint -> "(\\mu X_${this.id}.${this.body.asString()})"
    is Mu.Star -> if (this.expr.isAtom()) "${this.expr.asString()}^*" else "(${this.expr.asString()})^*"
}

private fun Mu.rewriteRule(): Mu {
    val skip = (
        this !is Mu.FixedPoint ||           // we look for (μX.(...)
        this.body !is Mu.Union ||           // (...) mX + ...
        this.body.lhs !is Mu.Concat ||      // mX is a Concat
        this.body.lhs.rhs !is Mu.RecVar ||  // X is a RecVar ...
        this.body.lhs.rhs.id != this.id     // that is equal to X in μX.
    )
    if (skip) return this.passOnTransform(Mu::rewriteRule)

    return Mu.mkFixedPoint(
        this.id,
        Mu.mkConcat(
            Mu.mkStar(this.body.lhs.lhs),
            this.body.rhs.rewriteRule()
        )
    )
    // TODO can I put Leiß' normal case in here?
}

private fun Mu.passOnTransform(f: (Mu) -> Mu): Mu = when (this) {
    is Mu.FixedPoint -> Mu.mkFixedPoint(this.id, f(this.body))
    is Mu.Union -> Mu.mkUnion(
        f(this.lhs),
        f(this.rhs)
    )
    is Mu.Concat -> Mu.mkConcat(
        f(this.lhs),
        f(this.rhs)
    )
    else -> this
}
