package org.example

enum class ArithCompOp {
    EQ, NEQ, LE, GE, LT, GT,
}

enum class ArithOp {
    PLUS, MINUS, DIVIDE, MULTIPLY, MODULO,
}

enum class BoolOp {
    IMPLY, RIMPLY, EQUIV, NEQUIV, AND, OR, XOR,
}

sealed interface Expr
data class Old(val name: String): Expr
data class Variable(val name: String) : Expr
data class Value(val value: Int) : Expr
data class ArithExpr(val op: ArithOp, val lhs: Expr, val rhs: Expr): Expr

sealed interface BoolExpr
data class Lit(val value: Boolean): BoolExpr
data class Not(val expr: BoolExpr): BoolExpr
data class BinOp(val op: BoolOp, val lhs: BoolExpr, val rhs: BoolExpr): BoolExpr
data class ArithComp(val op: ArithCompOp, val lhs: Expr, val rhs: Expr): BoolExpr

data class Contract(val precond: BoolExpr, val postcond: BoolExpr)

fun Expr.isReferenceTo(name: String): Boolean = when (this) {
    is Variable -> this.name == name
    is Old -> this.name == name
    else -> false
}

fun BoolExpr.boundary(wrt: String): Boundary {
    when (this) {
        is ArithComp -> {
            val idxOfVariable = arrayOf(this.lhs, this.rhs)
            .indexOfFirst { it.isReferenceTo(wrt) }
            if (idxOfVariable < 0) return Boundary.UNRELATED

            if (this.op != ArithCompOp.EQ) return Boundary.UNBOUND

            val other = if (idxOfVariable == 0) this.rhs else this.lhs
            if (other !is Value && !other.isReferenceTo(wrt)) return Boundary.UNBOUND

            return Boundary.BOUND
        }
        is BinOp -> {
            val ctor: (Boundary, Boundary) -> Boundary = when (this.op) {
                BoolOp.IMPLY -> { a, b -> a impl b }
                BoolOp.RIMPLY -> { a, b -> b impl a }
                BoolOp.EQUIV -> { a, b -> (a and b) or (a.neg() and b.neg()) }
                BoolOp.NEQUIV -> { a, b -> (a and b.neg()) or (a.neg() and b) }
                BoolOp.AND -> Boundary::and
                BoolOp.OR -> Boundary::or
                BoolOp.XOR -> { a, b -> (a and b.neg()) or (a.neg() and b) }
            }
            return ctor(this.lhs.boundary(wrt), this.rhs.boundary(wrt))
        }
        is Not -> return this.expr.boundary(wrt).neg()
        else -> return Boundary.UNRELATED
    }
}


fun BoolExpr.collectValuesInto(here: MutableSet<Int>, ofName: String): Unit {
    when (this) {
        is ArithComp -> {
            val idxOfVariable = arrayOf(this.lhs, this.rhs)
            .indexOfFirst { it.isReferenceTo(ofName) }
            if (idxOfVariable < 0) return

            val other = if (idxOfVariable == 0) this.rhs else this.lhs
            if (other !is Value) return

            here.add(other.value)
        }
        is BinOp -> {
            this.lhs.collectValuesInto(here, ofName)
            this.rhs.collectValuesInto(here, ofName)
        }
        is Not -> this.expr.collectValuesInto(here, ofName)
        else -> {}
    }
}
