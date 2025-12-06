package org.example

import com.microsoft.z3.*

import org.example.types.Expr as MyExpr
import org.example.types.BoolExpr as MyBoolExpr
import org.example.types.*

fun possibleTransitions(stateVar: StateVar, contract: Contract): List<Pair<Int, Int>> {
    val expr = MyBoolExpr.BinOp(BoolOp.AND, makeOld(contract.precond), contract.postcond)

    val ctx = Context()
    val solver = ctx.mkSolver()

    val z3Expr = toZ3Expr(ctx, expr)

    val newTerm = ctx.mkIntConst(stateVar.name)
    val oldTerm = ctx.mkIntConst(stateVar.oldName)
    val stateVarTerms = arrayOf(newTerm, oldTerm)

    val possibleStates = stateVarTerms
        .map { state ->
            stateVar.values.map { value ->
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

private fun oneTransition(stateVar: StateVar, model: Model): Pair<Int, Int> {
    val mappings = model.getConstDecls()
        .map {
            val interp = model.getConstInterp(it)
            check(interp is IntNum)
            val value = interp.getInt()
            Pair(it.getName().toString(), value)
        }

    val oldmap = mappings
        .first { (name, _) -> name.equals(stateVar.oldName) }!!
        .component2()
    val newmap = mappings
        .first { (name, _) -> name.equals(stateVar.name) }!!
        .component2()

    return TODO()
}

private fun blockModel(ctx: Context, solver: Solver, terms: Array<IntExpr>): Unit {
    val model = solver.getModel()
    val blockingTerm = terms
        .map { ctx.mkNot(ctx.mkEq(it, model.eval(it, true))) }
        .reduce { a, b -> ctx.mkOr(a, b) }
    solver.add(blockingTerm)
}

private fun makeOld(e: MyBoolExpr): MyBoolExpr = when (e) {
    is MyBoolExpr.Not -> MyBoolExpr.Not(makeOld(e.expr))
    is MyBoolExpr.BinOp -> MyBoolExpr.BinOp(e.op, makeOld(e.lhs), makeOld(e.rhs))
    is MyBoolExpr.ArithComp -> MyBoolExpr.ArithComp(e.op, makeOld(e.lhs), makeOld(e.rhs))
    else -> e
}

private fun makeOld(expr: MyExpr): MyExpr = when (expr) {
    is MyExpr.Old -> error("expected a precondition")
    is MyExpr.Variable -> MyExpr.Old(expr.name)
    is MyExpr.ArithExpr -> MyExpr.ArithExpr(expr.op, makeOld(expr.lhs), makeOld(expr.rhs))
    else -> expr
}

private fun toZ3Expr(ctx: Context, e: MyBoolExpr): BoolExpr = when (e) {
    is MyBoolExpr.Lit -> ctx.mkBool(e.value)
    is MyBoolExpr.Not -> ctx.mkNot(toZ3Expr(ctx, e.expr))
    is MyBoolExpr.BinOp -> {
        val ctor = boolOpCtor(ctx, e.op)
        ctor(toZ3Expr(ctx, e.lhs), toZ3Expr(ctx, e.rhs))
    }
    is MyBoolExpr.ArithComp -> {
        val ctor = arithCompCtor(ctx, e.op)
        ctor(toZ3Expr(ctx, e.lhs), toZ3Expr(ctx, e.rhs))
    }
}

private fun oldname(name: String) = "__OLD_" + name

private fun toZ3Expr(ctx: Context, e: MyExpr): ArithExpr<IntSort> = when (e) {
    is MyExpr.Old -> ctx.mkIntConst(oldname(e.name))
    is MyExpr.Variable -> ctx.mkIntConst(e.name)
    is MyExpr.Value -> ctx.mkInt(e.value)
    is MyExpr.ArithExpr -> {
        val ctor = arithOpCtor(ctx, e.op)
        ctor(toZ3Expr(ctx, e.lhs), toZ3Expr(ctx, e.rhs))
    }
}

private fun boolOpCtor(ctx: Context, op: BoolOp): (BoolExpr, BoolExpr) -> BoolExpr = when (op) {
    BoolOp.IMPLY -> { a, b -> ctx.mkImplies(a, b) }
    BoolOp.RIMPLY -> { a, b -> ctx.mkImplies(b, a) }
    BoolOp.EQUIV -> { a, b -> ctx.mkIff(a, b) }
    BoolOp.NEQUIV -> { a, b -> ctx.mkXor(a, b) }
    BoolOp.AND -> { a, b -> ctx.mkAnd(a, b) }
    BoolOp.OR -> { a, b -> ctx.mkOr(a, b) }
    BoolOp.XOR -> { a, b -> ctx.mkXor(a, b) }
}

private fun arithOpCtor(ctx: Context, op: ArithOp): (ArithExpr<IntSort>, ArithExpr<IntSort>) -> ArithExpr<IntSort> = when (op) {
    ArithOp.PLUS -> { a, b -> ctx.mkAdd(a, b) }
    ArithOp.MINUS -> { a, b -> ctx.mkSub(a, b) }
    ArithOp.DIVIDE -> { a, b -> ctx.mkDiv(a, b) }
    ArithOp.MULTIPLY -> { a, b -> ctx.mkMul(a, b) }
    ArithOp.MODULO -> { a, b -> ctx.mkMod(a, b) }
}

private fun arithCompCtor(ctx: Context, op: ArithCompOp): (ArithExpr<IntSort>, ArithExpr<IntSort>) -> BoolExpr = when (op) {
    ArithCompOp.EQ -> { a, b -> ctx.mkEq(a, b) }
    ArithCompOp.NEQ -> { a, b -> ctx.mkNot(ctx.mkEq(a, b)) }
    ArithCompOp.LE -> { a, b -> ctx.mkLe(a, b) }
    ArithCompOp.GE -> { a, b -> ctx.mkGe(a, b) }
    ArithCompOp.LT -> { a, b -> ctx.mkLt(a, b) }
    ArithCompOp.GT -> { a, b -> ctx.mkGt(a, b) }
}
