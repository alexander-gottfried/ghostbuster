package org.example

import kotlin.jvm.optionals.*

import com.github.javaparser.*
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

import com.github.javaparser.symbolsolver.*

data class Candidate(
    val name: String, 
    val initState: Int, 
    val states: List<Int>, 
    val methods: List<Pair<String, org.example.types.Contract>>
)

fun findStateVariable(compUnit: CompilationUnit): List<Candidate> {
    data class MutCandidate(
        val initState: Int,
        val states: MutableSet<Int>,
        val methodDecls: MutableSet<MethodDeclaration>
    )

    val candidates = mutableMapOf<String, MutCandidate>()

    val ghostFinder = object : VoidVisitorAdapter<Unit>() {
        override fun visit(jmlDecl: JmlFieldDeclaration, arg: Unit): Unit {
            val decl = jmlDecl.getDecl()

            // TODO add models
            var isGhost = false
            for (modifier in decl.getModifiers()) {
                isGhost = isGhost
                    || (modifier.getKeyword() == Modifier.DefaultKeyword.JML_GHOST)
            }
            if (!isGhost) return

            for (variable in decl.getVariables()) {
                val init = variable.getInitializer().getOrNull()

                // s_0 needs to exist
                if (init == null) continue

                // TODO constants
                val validType = init is IntegerLiteralExpr
                if (!validType) continue

                // extract
                val initVal = init.asNumber() as Int
                val name = variable.getName().asString()

                candidates.put(
                    name,
                    MutCandidate(
                        initVal,
                        mutableSetOf(initVal),
                        mutableSetOf<MethodDeclaration>())
                )
            }
        }
    }
    ghostFinder.visit(compUnit, Unit)

    val compChecker = object : VoidVisitorAdapter<Unit>() {
        override fun visit(expr: BinaryExpr, arg: Unit): Unit {
            val op = expr.getOperator()
            fun doTheThing(a: Expression, b: Expression): Boolean {
                // a should refer to a variable and be a candidate
                if (a !is NameExpr) return false
                val name = a.getName().asString()

                val candidate = candidates.get(name)
                // name not a candidate
                if (candidate == null) return false

                val comparedToIntLit = b is IntegerLiteralExpr
                    && op == BinaryExpr.Operator.EQUALS

                // name should only be compared to intlits
                if (!comparedToIntLit) {
                    candidates.remove(name)
                    return false
                }

                // only used in requires and ensures
                val clause = a.getParentNodeOfType(JmlSimpleExprClause::class.java).orElse(null)
                val inPreOrPostcondition = clause != null
                    && (clause.kind == JmlClauseKind.REQUIRES
                        || clause.kind == JmlClauseKind.ENSURES)

                if (!inPreOrPostcondition) {
                    candidates.remove(name)
                    return false
                }

                // we passed the requirements, now add data

                val methodDecl = a.getParentNodeOfType(MethodDeclaration::class.java).orElse(null)
                if (methodDecl == null) return false // shouldn't happen if above passed

                val value = b.asNumber() as Int // dangerous???

                candidate.methodDecls.add(methodDecl)
                candidate.states.add(value)
                return true
            }
            val left = expr.getLeft()
            val right = expr.getRight()
            // ... down here
            if (doTheThing(left, right)) return
            doTheThing(right, left)
        }
    }
    compChecker.visit(compUnit, Unit)

    val result = candidates
        .filterValues { (_, states, _) -> states.size > 1 }
        .map { (name, mutCandidate) ->
            val (initVal, states, methodDecls) = mutCandidate
            val methods = methodDecls.map {
                val name = it.getName().asString()
                val contract = contractFromMethod(it)
                Pair(name, contract)
            }
            Candidate(name, initVal, states.toList(), methods)
        }

    println(result)

    return result
}

private fun contractFromMethod(methodDecl: MethodDeclaration): List<org.example.types.Clause> {
    val clauses = methodDecl.getContracts()
        .filter {
            val b = it.getBehavior()
            b == Behavior.NONE || b == Behavior.NORMAL
        }
        .map {
            it.getClauses()
                .filterIsInstance<JmlSimpleExprClause>()
                .map {
                    when (it.kind) {
                        JmlClauseKind.REQUIRES -> Pair(
                            org.example.types.ClauseKind.REQUIRES,
                            it.getExpression()
                        )
                        JmlClauseKind.ENSURES -> Pair(
                            org.example.types.ClauseKind.ENSURES,
                            it.getExpression()
                        )
                        else -> null
                    }
                }
                .filterNotNull()
                .map { (kind, expr) ->
                    org.example.types.Clause(
                        kind,
                        exprFromJmlParserExpression(expr)
                    )
                }
        }
    // TODO a method may have mutliple contracts
    if (clauses.size != 1) error("we require that only one contract exists, for now")
    return clauses.first()
}

private fun exprFromJmlParserExpression(e: Expression): org.example.types.Expr = when (e) {
    is BinaryExpr -> org.example.types.BinExpr(
        e.getOperator(),
        exprFromJmlParserExpression(e.getLeft()),
        exprFromJmlParserExpression(e.getRight())
    )
    is NameExpr -> org.example.types.Variable(e.getName().asString())
    is IntegerLiteralExpr -> org.example.types.Value(e.asNumber() as Int)
    is MethodCallExpr -> {
        val callName = e.getName().asString()
        val callArgs = e.getArguments()
        check(callName == "\\old" && callArgs.size == 1)

        val arg = callArgs.first()
        check(arg is NameExpr)

        org.example.types.Old(arg.getName().asString())
    }
    else -> error("TODO we don't do other expressions yet")
}
