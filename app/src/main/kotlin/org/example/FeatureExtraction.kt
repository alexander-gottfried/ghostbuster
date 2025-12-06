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

import org.example.types.Expr as MyExpr
import org.example.types.BoolExpr as MyBoolExpr
import org.example.types.Contract as MyContract
import org.example.types.*

data class StateVar(val name: String, val oldName: String, val values: List<Int>) {
    companion object {
        fun make(name: String, values: List<Int>)
            = StateVar(name, "__OLD_" + name, values)
    }
}

data class Candidate(
    val name: StateVar,
    val initState: Int, 
    val methods: List<Pair<String, MyContract>>
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
            val values = mutableSetOf<Int>()
            val methods = methodDecls.map {
                val mname = it.getName().asString()

                val jmlContract = it.getContracts().first()
                check(jmlContract.getBehavior() in arrayOf(Behavior.NONE, Behavior.NORMAL))
                val clauses = extractContract(jmlContract)

                val precond = clauses.filter { it.kind == ClauseKind.REQUIRES }
                    .map(Clause::expr)
                    .reduce { a, b -> MyBoolExpr.BinOp(BoolOp.AND, a, b) }
                val postcond = clauses.filter { it.kind == ClauseKind.ENSURES }
                    .map(Clause::expr)
                    .reduce { a, b -> MyBoolExpr.BinOp(BoolOp.AND, a, b) }

                collectValuesInto(precond, name, values)
                collectValuesInto(postcond, name, values)

                Pair(mname, MyContract(precond, postcond))
            }
            println(values)
            Candidate(StateVar.make(name, values.toList()), initVal, methods)
        }

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
        .map { (kind, expr) -> Clause( kind, extractBoolExpr(expr)) }

private fun extractBoolExpr(e: Expression): MyBoolExpr = when (e) {
    is BooleanLiteralExpr -> MyBoolExpr.Lit(e.getValue())
    is UnaryExpr if e.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT -> {
        MyBoolExpr.Not(extractBoolExpr(e.getExpression()))
    }
    is BinaryExpr -> extractBinaryExpr(e)
    else -> error("")
}

private fun extractBinaryExpr(e: BinaryExpr): MyBoolExpr {
    val exop = e.getOperator()

    val op1 = myBoolOp(exop)
    if (op1 != null) {
        return MyBoolExpr.BinOp(
            op1,
            extractBoolExpr(e.getLeft()),
            extractBoolExpr(e.getRight())
        )
    }

    val op2 = myArithCompOp(exop)
    if (op2 != null) {
        return MyBoolExpr.ArithComp(
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

private fun extractExpr(e: Expression): MyExpr = when (e) {
    is BinaryExpr -> MyExpr.ArithExpr(
        myArithOp(e.getOperator()),
        extractExpr(e.getLeft()),
        extractExpr(e.getRight())
    )
    is NameExpr -> MyExpr.Variable(e.getName().asString())
    is IntegerLiteralExpr -> MyExpr.Value(e.asNumber() as Int)
    is MethodCallExpr -> {
        val callName = e.getName().asString()
        val callArgs = e.getArguments()
        check(callName == "\\old" && callArgs.size == 1)

        val arg = callArgs.first()
        check(arg is NameExpr)

        MyExpr.Old(arg.getName().asString())
    }
    else -> error("TODO we don't do other expressions yet")
}
