package org.example

data class Candidate(val name: String, val init: Int)
data class Method(val name: String, val precond: BoolExpr, val postcond: BoolExpr)

data class Transition(val from: Int, val to: Int, val method: String)
data class StateVariable(
    val name: String,
    val init: Int,
    val values: List<Int>,
    val transitions: List<Transition>,
)

private data class MethodBoundary(val method: Method, val preb: Boundary, val postb: Boundary)

fun getStateVariable(candidate: Candidate, allMethods: List<Method>): StateVariable? {
    val name = candidate.name

    val boundaries = allMethods.map {
        val (name, precond, postcond) = it
        MethodBoundary(it, precond.boundary(candidate.name), postcond.boundary(candidate.name))
    }
    .filterNot { (_, preb, postb) -> preb.isUnrelated() && postb.isUnrelated() }

    val varIsUnbound = boundaries.any { (_, preb, postb) ->
        preb.isUnbound() || postb.isUnbound()
    }

    if (varIsUnbound || boundaries.size <= 1) return null

    val methods = boundaries.map(MethodBoundary::method)

    // TODO we should check if the initial state is in here
    val tmp = mutableSetOf<Int>()
    methods.forEach { (_, pre, post) ->
        pre.collectValuesInto(tmp, name)
        post.collectValuesInto(tmp, name)
    }
    if (candidate.init !in tmp) return null
    val values = tmp.toList()

    val oldName = "__OLD_" + name
    val transitions = mutableSetOf<Transition>()

    methods.forEach { (mname, prec, postc) ->
        val ts = possibleTransitions(name, oldName, values, prec, postc).map { (from, to) ->
            Transition(from, to, mname)
        }
        transitions.addAll(ts)
    }

    return StateVariable(candidate.name, candidate.init, values, transitions.toList())
}
