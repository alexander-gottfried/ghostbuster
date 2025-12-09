package org.example

import com.microsoft.z3.Expr as Z3Expr
import com.microsoft.z3.ArithExpr as Z3ArithExpr
import com.microsoft.z3.BoolExpr as Z3BoolExpr
import com.microsoft.z3.*

fun possibleTransitions(
    name: String,
    oldName: String,
    values: List<Int>,
    precondition: BoolExpr,
    postcondition: BoolExpr
): List<Pair<Int, Int>> {
    val expr = BinOp(BoolOp.AND, makeOld(precondition), postcondition)

    val ctx = Context()
    val solver = ctx.mkSolver()

    val z3Expr = toZ3Expr(ctx, expr)

    val newTerm = ctx.mkIntConst(name)
    val oldTerm = ctx.mkIntConst(oldName)
    val stateVarTerms = arrayOf(newTerm, oldTerm)

    val possibleStates = stateVarTerms.map { state ->
        values.map { value ->
            ctx.mkEq(state, ctx.mkInt(value))
        }
        .reduce { a, b -> ctx.mkOr(a, b) }
    }
    .reduce { a, b -> ctx.mkAnd(a, b) }


    solver.add(z3Expr)
    solver.add(possibleStates)
    solver.getAssertions().forEach(::println)

    var i = 0

    val transitions = mutableSetOf<Pair<Int, Int>>()

    fun evalTerm(model: Model, term: IntExpr): Int {
        val evaluated = model.eval(term, true)
        check(evaluated is IntNum)
        return evaluated.getInt()
    }

    while (i < 10 && solver.check() == Status.SATISFIABLE) {
        i++
        val model = solver.getModel()
        val transition = Pair(
            evalTerm(model, oldTerm),
            evalTerm(model, newTerm)
        )
        transitions.add(transition)
        blockModel(ctx, solver, stateVarTerms)
    }

    ctx.close()
    println(transitions)
    return transitions.toList()
}

/*
private fun oneTransition(name: String, oldName: String, model: Model): Pair<Int, Int> {
    val mappings = model.getConstDecls()
        .map {
            val interp = model.getConstInterp(it)
            check(interp is IntNum)
            val value = interp.getInt()
            Pair(it.getName().toString(), value)
        }

    val oldmap = mappings
        .first { (name, _) -> name.equals(oldName) }!!
        .component2()
    val newmap = mappings
        .first { (name, _) -> name.equals(name) }!!
        .component2()

    return TODO()
}
*/

private fun blockModel(ctx: Context, solver: Solver, terms: Array<IntExpr>): Unit {
    val model = solver.getModel()
    val blockingTerm = terms
        .map { ctx.mkNot(ctx.mkEq(it, model.eval(it, true))) }
        .reduce { a, b -> ctx.mkOr(a, b) }
    solver.add(blockingTerm)
}

private fun makeOld(e: BoolExpr): BoolExpr = when (e) {
    is Not -> Not(makeOld(e.expr))
    is BinOp -> BinOp(e.op, makeOld(e.lhs), makeOld(e.rhs))
    is ArithComp -> ArithComp(e.op, makeOld(e.lhs), makeOld(e.rhs))
    else -> e
}

private fun makeOld(expr: Expr): Expr = when (expr) {
    is Old -> error("expected a precondition")
    is Variable -> Old(expr.name)
    is ArithExpr -> ArithExpr(expr.op, makeOld(expr.lhs), makeOld(expr.rhs))
    else -> expr
}

private fun toZ3Expr(ctx: Context, e: BoolExpr): Z3BoolExpr = when (e) {
    is Lit -> ctx.mkBool(e.value)
    is Not -> ctx.mkNot(toZ3Expr(ctx, e.expr))
    is BinOp -> {
        val ctor = boolOpCtor(ctx, e.op)
        ctor(toZ3Expr(ctx, e.lhs), toZ3Expr(ctx, e.rhs))
    }
    is ArithComp -> {
        val ctor = arithCompCtor(ctx, e.op)
        ctor(toZ3Expr(ctx, e.lhs), toZ3Expr(ctx, e.rhs))
    }
}

private fun oldname(name: String) = "__OLD_" + name

private fun toZ3Expr(ctx: Context, e: Expr): Z3ArithExpr<IntSort> = when (e) {
    is Old -> ctx.mkIntConst(oldname(e.name))
    is Variable -> ctx.mkIntConst(e.name)
    is Value -> ctx.mkInt(e.value)
    is ArithExpr -> {
        val ctor = arithOpCtor(ctx, e.op)
        ctor(toZ3Expr(ctx, e.lhs), toZ3Expr(ctx, e.rhs))
    }
}

private fun boolOpCtor(
    ctx: Context,
    op: BoolOp
): (Z3BoolExpr, Z3BoolExpr) -> Z3BoolExpr = when (op) {
    BoolOp.IMPLY -> { a, b -> ctx.mkImplies(a, b) }
    BoolOp.RIMPLY -> { a, b -> ctx.mkImplies(b, a) }
    BoolOp.EQUIV -> { a, b -> ctx.mkIff(a, b) }
    BoolOp.NEQUIV -> { a, b -> ctx.mkXor(a, b) }
    BoolOp.AND -> { a, b -> ctx.mkAnd(a, b) }
    BoolOp.OR -> { a, b -> ctx.mkOr(a, b) }
    BoolOp.XOR -> { a, b -> ctx.mkXor(a, b) }
}

private fun arithOpCtor(
    ctx: Context,
    op: ArithOp
): (Z3ArithExpr<IntSort>, Z3ArithExpr<IntSort>) -> Z3ArithExpr<IntSort> = when (op) {
    ArithOp.PLUS -> { a, b -> ctx.mkAdd(a, b) }
    ArithOp.MINUS -> { a, b -> ctx.mkSub(a, b) }
    ArithOp.DIVIDE -> { a, b -> ctx.mkDiv(a, b) }
    ArithOp.MULTIPLY -> { a, b -> ctx.mkMul(a, b) }
    ArithOp.MODULO -> { a, b -> ctx.mkMod(a, b) }
}

private fun arithCompCtor(
    ctx: Context,
    op: ArithCompOp
): (Z3ArithExpr<IntSort>, Z3ArithExpr<IntSort>) -> Z3BoolExpr = when (op) {
    ArithCompOp.EQ -> { a, b -> ctx.mkEq(a, b) }
    ArithCompOp.NEQ -> { a, b -> ctx.mkNot(ctx.mkEq(a, b)) }
    ArithCompOp.LE -> { a, b -> ctx.mkLe(a, b) }
    ArithCompOp.GE -> { a, b -> ctx.mkGe(a, b) }
    ArithCompOp.LT -> { a, b -> ctx.mkLt(a, b) }
    ArithCompOp.GT -> { a, b -> ctx.mkGt(a, b) }
}
