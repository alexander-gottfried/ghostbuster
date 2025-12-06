package org.example

import java.io.File
import java.io.FileInputStream

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

fun parse(file: File): CompilationUnit {
    val config = ParserConfiguration()
    config.setProcessJml(true)
    val parser = JavaParser(config)

    val inputStream = FileInputStream(file)
    val compilationUnit = parser
        .parse(inputStream)
        .getResult().get()

    val printer = YamlPrinter(true)
    println(printer.output(compilationUnit))

    return compilationUnit
}
