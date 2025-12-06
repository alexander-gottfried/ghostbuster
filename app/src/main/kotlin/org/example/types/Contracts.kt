package org.example.types

import com.github.javaparser.ast.expr.BinaryExpr
typealias Operator = BinaryExpr.Operator

enum class ArithCompOp {
    EQ, NEQ, LE, GE, LT, GT,
}

enum class ArithOp {
    PLUS, MINUS, DIVIDE, MULTIPLY, MODULO,
}

enum class BoolOp {
    IMPLY, RIMPLY, EQUIV, NEQUIV, AND, OR, XOR,
}

sealed interface Expr {
    data class Old(val name: String): Expr
    data class Variable(val name: String) : Expr
    data class Value(val value: Int) : Expr
    data class ArithExpr(val op: ArithOp, val lhs: Expr, val rhs: Expr): Expr
}

sealed interface BoolExpr {
    data class Lit(val value: Boolean): BoolExpr
    data class Not(val expr: BoolExpr): BoolExpr
    data class BinOp(val op: BoolOp, val lhs: BoolExpr, val rhs: BoolExpr): BoolExpr
    data class ArithComp(val op: ArithCompOp, val lhs: Expr, val rhs: Expr): BoolExpr
}

fun collectValuesInto(
    inHere: BoolExpr,
    ofThisVar: String,
    intoHere: MutableSet<Int>
): Unit {
    when (inHere) {
        is BoolExpr.Not -> collectValuesInto(inHere.expr, ofThisVar, intoHere)
        is BoolExpr.BinOp -> {
            collectValuesInto(inHere.lhs, ofThisVar, intoHere)
            collectValuesInto(inHere.rhs, ofThisVar, intoHere)
        }
        is BoolExpr.ArithComp -> {
            val hands = arrayOf(inHere.lhs, inHere.rhs)
            val nameExpr = hands.first { it is Expr.Variable || it is Expr.Old }
            val valueExpr = hands.first { it is Expr.Value }
            if (nameExpr == null || valueExpr == null) return

            val value = valueExpr as Expr.Value
            when (nameExpr) {
                is Expr.Variable if nameExpr.name == ofThisVar -> intoHere.add(value.value)
                is Expr.Old if nameExpr.name == ofThisVar -> intoHere.add(value.value)
                else -> return
            }
        }
        else -> {}
    }
}

enum class ClauseKind { REQUIRES, ENSURES }
data class Clause(val kind: ClauseKind, val expr: BoolExpr) 
//typealias Contract = List<Clause>
data class Contract(val precond: BoolExpr, val postcond: BoolExpr)
