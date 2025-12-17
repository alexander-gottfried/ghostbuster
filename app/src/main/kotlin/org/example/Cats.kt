package org.example

// this definition is specific to my use case,
// hence there are nomfsm, nomfsmExcept, and Run, which is not a real event
// also no states
sealed interface Cat {
    class False : Cat
    data class Run(val method: String) : Cat

    class AnyEvent : Cat
    class NoMfsm : Cat
    data class NoMfsmExcept(val these: List<String>) : Cat
    //class Generic(val exclude: Event) : Cat
    
    fun isGeneric(): Boolean = (
        this is Cat.AnyEvent
        || this is Cat.NoMfsm
        || this is Cat.NoMfsmExcept
        || (this is Cat.Concat && this.lhs.isGeneric())
    )

    data class RecVar(val id: Int) : Cat
    data class FixedPoint(val id: Int, val body: Cat) : Cat

    data class Union(val lhs: Cat, val rhs: Cat) : Cat
    data class Intersection(val lhs: Cat, val rhs: Cat) : Cat
    data class Concat(val lhs: Cat, val rhs: Cat) : Cat

    // TODO observations, but I don't need them for now
    companion object {
        fun mkConcat(lhs: Cat, rhs: Cat): Cat = when {
            lhs is Cat.Concat -> Cat.mkConcat(lhs.lhs, Cat.mkConcat(lhs.rhs, rhs))
            lhs.isGeneric() && rhs is Cat.NoMfsm -> Cat.NoMfsm()
            else -> Cat.Concat(lhs, rhs)
        }
        fun mkUnion(lhs: Cat, rhs: Cat): Cat = when (lhs) {
            is Cat.Union -> Cat.Union(lhs.lhs, Cat.mkUnion(lhs.rhs, rhs))
            else -> Cat.Union(lhs, rhs)
        }
        fun mkIntersection(lhs: Cat, rhs: Cat): Cat = when (lhs) {
            is Cat.Intersection -> Cat.Intersection(lhs.lhs, Cat.mkIntersection(lhs.rhs, rhs))
            else -> Cat.Intersection(lhs, rhs)
        }
    }
}

fun Mu.toCat(): Cat = when (this) {
    is Mu.Zero -> Cat.False()
    is Mu.One -> Cat.NoMfsm()
    is Mu.Sym -> Cat.mkConcat(
        Cat.NoMfsm(),
        Cat.mkConcat(
            Cat.Run(this.name),
            Cat.NoMfsm()
        )
    )
    is Mu.Union -> Cat.mkUnion(this.lhs.toCat(), this.rhs.toCat())
    is Mu.Concat -> Cat.mkConcat(this.lhs.toCat(), this.rhs.toCat())
    is Mu.RecVar -> Cat.RecVar(this.id)
    is Mu.FixedPoint -> Cat.FixedPoint(this.id, this.body.toCat())
    // I assume inputs are well-formed, and body contains no free variables
    is Mu.Star -> when (this.expr) {
        is Mu.Sym -> Cat.NoMfsmExcept(listOf(this.expr.name))
        else -> Mu.mkFixedPoint(1,
            Mu.mkUnion(Mu.mkOne(), Mu.mkConcat(this.expr, Mu.mkRecVar(1)))
        ).toCat()
    }
}

fun Cat.asLatex(): String = when (this) {
    is Cat.False -> "\\stateFml{\\mathrm{False}}"
    is Cat.Run -> "\\runm{${this.method}}"
    is Cat.AnyEvent -> "\\anyEvent"
    is Cat.NoMfsm -> "\\nomfsm"
    is Cat.NoMfsmExcept -> {
        val content = this.these.joinToString(", ")
        "\\nomfsmExcept{$content}"
    }
    is Cat.RecVar -> "X_${this.id}"
    is Cat.FixedPoint -> "(\\mu X_${this.id}.${this.body.asLatex()})"
    is Cat.Union -> "${this.lhs.asLatex()} \\lor ${this.rhs.asLatex()}"
    is Cat.Intersection -> {
        fun paren(c: Cat): String = if (c is Cat.Union) "(${c.asLatex()})" else c.asLatex()
        "${paren(this.lhs)} \\land ${paren(this.rhs)}"
    }
    is Cat.Concat -> {
        fun paren(c: Cat): String = if (c is Cat.Union || c is Cat.Intersection) {
            "(${c.asLatex()})"
        } else {
            c.asLatex()
        }
        val op = if (this.lhs.isGeneric() || this.rhs.isGeneric()) " " else " \\cdot "
        "${paren(this.lhs)}$op${paren(this.rhs)}"
    }

}
