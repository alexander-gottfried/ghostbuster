package org.example

import java.io.File
import java.io.FileInputStream

import kotlin.jvm.optionals.getOrNull

import com.github.javaparser.*
import com.github.javaparser.printer.*
import com.github.javaparser.ast.*
import com.github.javaparser.ast.visitor.*
import com.github.javaparser.ast.jml.*

import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.jml.*
import com.github.javaparser.ast.jml.body.*
import com.github.javaparser.ast.jml.expr.*
import com.github.javaparser.ast.jml.stmt.*
import com.github.javaparser.ast.jml.clauses.*

fun parse(file: File): Pair<List<Candidate>, List<Method>> {
    // setting up symbol solver, for resolving names
    /*
    val typeSolver = JavaParserTypeSolver(
        file.getAbsoluteFile().getParentFile()
    )
    */
    //val symbolSolver = JavaSymbolSolver(typeSolver)

    // setting up parser
    val parser = JavaParser()
    parser.getParserConfiguration()
        .setProcessJml(true)
        //.setSymbolResolver(symbolSolver)

    val inputStream = FileInputStream(file)
    val compilationUnit = parser
        .parse(inputStream)
        .getResult().get()

// I won't actually use the SymbolSolver because it's being super difficult.
// 1) JavaParser won't use the SymbolSolver for some reason.
//    StaticJavaParser won't accept the 'ghost' token.
// 2) NameExprs that refer to ghosts don't resolve to a declaration.
// So the objectively correct approach just doesn't work for JML.

    val candidates = mutableSetOf<Candidate>()
    val methods = mutableListOf<Method>()

    GhostCollector(candidates).visit(compilationUnit, Unit)
    MethodCollector(methods).visit(compilationUnit, Unit)

    return Pair(candidates.toList(), methods)
}

private class GhostCollector(val candidates: MutableSet<Candidate>) : VoidVisitorAdapter<Unit>() {
    override fun visit(jmlDecl: JmlFieldDeclaration, arg: Unit): Unit {
        val decl = jmlDecl.getDecl()

        // Def. 3.2 b) ghosts only
        val isGhost = decl.getModifiers().any {
            it.getKeyword() == Modifier.DefaultKeyword.JML_GHOST
        }
        if (!isGhost) return

        for (variable in decl.getVariables()) {
            // Def. 3.2 a) s_0 needs to exist
            val init = variable.getInitializer().getOrNull()
            if (init == null) continue

            // TODO constants
            // Def. 3.2 b) ints only
            val validType = init is IntegerLiteralExpr
            if (!validType) continue

            // extract
            val initVal = init.asNumber() as Int
            val name = variable.getName().asString()

            candidates.add(Candidate(name, initVal))
        }
    }
}

enum class ClauseKind { REQUIRES, ENSURES }
data class Clause(val kind: ClauseKind, val expr: BoolExpr) 

private class MethodCollector(val methods: MutableList<Method>) : VoidVisitorAdapter<Unit>() {
    override fun visit(decl: MethodDeclaration, arg: Unit): Unit {
        val name = decl.getName().asString()

        // TODO find definition that says only one contract
        val jmlContracts = decl.getContracts()
        if (jmlContracts.size == 0) return
        if (jmlContracts.size > 1) error("we don't allow multiple contracts for now")
        val clauses = extractContract(jmlContracts.first())

        // Def. 3.2 d) only requires/ensures
        val precond = clauses.filter { it.kind == ClauseKind.REQUIRES }
            .map(Clause::expr)
            .reduce { a, b -> BinOp(BoolOp.AND, a, b) }
        val postcond = clauses.filter { it.kind == ClauseKind.ENSURES }
            .map(Clause::expr)
            .reduce { a, b -> BinOp(BoolOp.AND, a, b) }

        methods.add(Method(name, precond, postcond))
    }
}

private fun extractContract(contract: JmlContract): List<Clause> =
    contract.getClauses()
        .filterIsInstance<JmlSimpleExprClause>()
        .mapNotNull {
            when (it.kind) {
                JmlClauseKind.REQUIRES -> Pair(
                    ClauseKind.REQUIRES,
                    it.getExpression()
                )
                JmlClauseKind.ENSURES -> Pair(
                    ClauseKind.ENSURES,
                    it.getExpression()
                )
                else -> null
            }
        }
        .map { (kind, expr) -> Clause( kind, extractBoolExpr(expr)) }

private fun extractBoolExpr(e: Expression): BoolExpr = when (e) {
    is BooleanLiteralExpr -> Lit(e.getValue())
    is UnaryExpr if e.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT -> {
        Not(extractBoolExpr(e.getExpression()))
    }
    is BinaryExpr -> extractBinaryExpr(e)
    else -> error("")
}

private fun extractBinaryExpr(e: BinaryExpr): BoolExpr {
    val exop = e.getOperator()

    val op1 = myBoolOp(exop)
    if (op1 != null) {
        return BinOp(
            op1,
            extractBoolExpr(e.getLeft()),
            extractBoolExpr(e.getRight())
        )
    }

    val op2 = myArithCompOp(exop)
    if (op2 != null) {
        return ArithComp(
            op2,
            extractExpr(e.getLeft()),
            extractExpr(e.getRight())
        )
    }

    error("other ops not supported here")
}

private fun myBoolOp(op: BinaryExpr.Operator): BoolOp? = when (op) {
    BinaryExpr.Operator.IMPLICATION -> BoolOp.IMPLY
    BinaryExpr.Operator.RIMPLICATION -> BoolOp.RIMPLY
    BinaryExpr.Operator.EQUIVALENCE -> BoolOp.EQUIV
    BinaryExpr.Operator.ANTIVALENCE -> BoolOp.NEQUIV
    BinaryExpr.Operator.OR -> BoolOp.OR
    BinaryExpr.Operator.AND -> BoolOp.AND
    BinaryExpr.Operator.XOR -> BoolOp.XOR
    else -> null
}

private fun myArithCompOp(op: BinaryExpr.Operator): ArithCompOp? = when (op) {
    BinaryExpr.Operator.EQUALS -> ArithCompOp.EQ
    BinaryExpr.Operator.NOT_EQUALS -> ArithCompOp.NEQ
    BinaryExpr.Operator.LESS_EQUALS -> ArithCompOp.LE
    BinaryExpr.Operator.GREATER_EQUALS -> ArithCompOp.GE
    BinaryExpr.Operator.LESS -> ArithCompOp.LT
    BinaryExpr.Operator.GREATER -> ArithCompOp.GT
    else -> null
}

private fun myArithOp(op: BinaryExpr.Operator): ArithOp = when (op) {
    BinaryExpr.Operator.PLUS -> ArithOp.PLUS
    BinaryExpr.Operator.MINUS -> ArithOp.MINUS
    BinaryExpr.Operator.DIVIDE -> ArithOp.DIVIDE
    BinaryExpr.Operator.MULTIPLY -> ArithOp.MULTIPLY
    BinaryExpr.Operator.REMAINDER -> ArithOp.MODULO
    else -> error("")
}

private fun extractExpr(e: Expression): Expr = when (e) {
    is BinaryExpr -> ArithExpr(
        myArithOp(e.getOperator()),
        extractExpr(e.getLeft()),
        extractExpr(e.getRight())
    )
    is NameExpr -> Variable(e.getName().asString())
    is IntegerLiteralExpr -> Value(e.asNumber() as Int)
    is MethodCallExpr -> {
        val callName = e.getName().asString()
        val callArgs = e.getArguments()
        check(callName == "\\old" && callArgs.size == 1)

        val arg = callArgs.first()
        check(arg is NameExpr)

        Old(arg.getName().asString())
    }
    else -> error("TODO we don't do other expressions yet")
}
