package org.example.types

import com.github.javaparser.ast.expr.BinaryExpr
typealias Operator = BinaryExpr.Operator

sealed interface Expr
data class Old(val name: String): Expr
data class Variable(val name: String) : Expr
data class Value(val value: Int) : Expr
data class BinExpr(
    val op: Operator,
    val lhs: Expr,
    val rhs: Expr
) : Expr

enum class ClauseKind { REQUIRES, ENSURES }
data class Clause(val kind: ClauseKind, val expr: Expr) 
typealias Contract = List<Clause>
