@file:JvmName("App")

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println(printGreeting("App", "v1", *args)) // spread operator

    val threshold = 0.05 // value is immutable (final)
    var apiUrl: String = "https://api-invest.tinkoff.ru/openapi/sandbox"
    println("Going to use '$apiUrl' to play a little bit") // string templates

    val environments = hashMapOf("sandbox" to apiUrl) // 'to' is a function here, infix call

    if (apiUrl.isNullOrBlank()) {
        exitProcess(0)
    }
}

// function with expression body (not block body)
// did you pay attention on type inference ?
fun printGreeting(vararg values: String) = if (true) "We are starting..." + values[0] + values[1] else "ha-ha, no way you see it"
    // 'if' is expression, not a construction
    // as well as other (not for, while). Also in Kotlin 'applying' is instruction, not expression