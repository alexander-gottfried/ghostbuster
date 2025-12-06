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

import org.example.types.Clause

data class Candidate(
    val name: String, 
    val initState: Int, 
    val methods: List<Pair<String, org.example.types.Contract>>
)

fun findStateVariable(compUnit: CompilationUnit): List<Candidate> {
    data class MutCandidate(
        val initState: Int,
        val methodDecls: MutableSet<MethodDeclaration>
    )

    val candidates = mutableMapOf<String, MutCandidate>()

    val ghostFinder = object : VoidVisitorAdapter<Unit>() {
        override fun visit(jmlDecl: JmlFieldDeclaration, arg: Unit): Unit {
            val decl = jmlDecl.getDecl()

            // TODO add models
            val isGhost = decl.getModifiers().any {
                it.getKeyword() == Modifier.DefaultKeyword.JML_GHOST
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
                        mutableSetOf<MethodDeclaration>())
                )
            }
        }
    }
    ghostFinder.visit(compUnit, Unit)

    val candidateChecker = object : VoidVisitorAdapter<Unit>() {
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

                candidate.methodDecls.add(methodDecl)
                return true
            }
            val left = expr.getLeft()
            val right = expr.getRight()
            // ... down here
            if (doTheThing(left, right)) return
            doTheThing(right, left)
        }
    }
    candidateChecker.visit(compUnit, Unit)

    val result = candidates
        .filterValues { (_, methodDecls) -> methodDecls.size > 1 }
        .map { (name, mutCandidate) ->
            val (initVal, methodDecls) = mutCandidate
            val methods = methodDecls.map {
                val mname = it.getName().asString()

                val jmlContract = it.getContracts().first()
                check(jmlContract.getBehavior() in arrayOf(Behavior.NONE, Behavior.NORMAL))
                val contract = extractContract(jmlContract)

                Pair(mname, contract)
            }
            Candidate(name, initVal, methods)
        }

    println(result)

    return result
}

private fun extractContract(contract: JmlContract): List<Clause> =
    contract.getClauses()
        .filterIsInstance<JmlSimpleExprClause>()
        .mapNotNull {
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
        .map { (kind, expr) -> Clause( kind, extractExpr(expr)) }

private fun extractExpr(e: Expression): org.example.types.Expr = when (e) {
    is BinaryExpr -> org.example.types.BinExpr(
        e.getOperator(),
        extractExpr(e.getLeft()),
        extractExpr(e.getRight())
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
