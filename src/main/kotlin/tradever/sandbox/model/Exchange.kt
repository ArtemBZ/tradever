package tradever.sandbox.model

interface Exchange
class ExchangeFakeImpl(val firms: Array<String>) : Exchange

fun getFirmsInExchange(exchange: Exchange): Array<String> {
    if (exchange is ExchangeFakeImpl) {
        val exchangeFake = exchange as ExchangeFakeImpl
        return exchangeFake.firms
    } else {
        return arrayOf()
    }
}

fun getFirmsInExchangeKotlinWay(exchange: Exchange): Array<String> =
    when (exchange) {
        is ExchangeFakeImpl -> {
            println("No-no, they are not real")
            exchange.firms  // wow, smart cast. That's why you should use val instead of var
        }
        else -> arrayOf()
    }

fun print5Firms(exchange: Exchange) {
    val firmsInExchangeKotlinWay = getFirmsInExchangeKotlinWay(exchange)
    for (i in 1..5) {
        println(firmsInExchangeKotlinWay[i])
    }

    for (i in 5 downTo 1 step 2) { // you can use until as well
        println(firmsInExchangeKotlinWay[i])
    }
}