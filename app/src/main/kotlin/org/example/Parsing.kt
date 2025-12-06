package org.example

import java.io.File
import java.io.FileInputStream

import com.github.javaparser.*
import com.github.javaparser.printer.*
import com.github.javaparser.ast.*

import com.github.javaparser.symbolsolver.*
import com.github.javaparser.symbolsolver.resolution.typesolvers.*

fun parse(file: File): CompilationUnit {
    // setting up symbol solver, for resolving names
    val typeSolver = JavaParserTypeSolver(
        file.getAbsoluteFile().getParentFile()
    )
    val symbolSolver = JavaSymbolSolver(typeSolver)

    // setting up parser
    val parser = JavaParser()
    parser.getParserConfiguration()
        .setProcessJml(true)
        .setSymbolResolver(symbolSolver)

    val inputStream = FileInputStream(file)
    val compilationUnit = parser
        .parse(inputStream)
        .getResult().get()

// I won't actually use the SymbolSolver because it's being super difficult.
// 1) JavaParser won't use the SymbolSolver for some reason.
//    StaticJavaParser won't accept the 'ghost' token.
// 2) NameExprs that refer to ghosts don't resolve to a declaration.
// So the objectively correct approach just doesn't work for JML.

    // TODO remove
    val printer = YamlPrinter(true)
    println(printer.output(compilationUnit))

    return compilationUnit
}
