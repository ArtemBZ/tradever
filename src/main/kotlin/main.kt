import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println(printGreeting())

    val threshold = 0.05 // value is immutable (final)
    var apiUrl: String = "https://api-invest.tinkoff.ru/openapi/sandbox"
    println("Going to use '$apiUrl' to play a little bit") // string templates

    if (apiUrl.isNullOrBlank()) {
        exitProcess(0)
    }
}

// function with expression body (not block body)
// did you pay attention on type inference ?
fun printGreeting() = if (true) "We are starting..." else "ha-ha, no way you see it"
    // 'if' is expression, not a construction
    // as well as other (not for, while). Also in Kotlin 'applying' is instruction, not expression