package tradever.model

import tradever.model.Currency.*

// value object
// public by default
// it has different name
class Element(val currentPrice: Double, var name: String) {
    val isCompleted: Boolean
        get() = false
}

enum class Currency(val title: String, val symbol: String) {
    US_DOLLAR("us dollar", "$"),
    CANADIAN_DOLLAR("canadian dollar", "$"),
    RUB("Rubl", "p"); // ';' !!!

    fun getSupportedCurrencies() = US_DOLLAR.title + RUB.title
}

fun getMinValue(currency: Currency) =
    // you can use set as well
    when (currency) {
        RUB -> 10
        US_DOLLAR, CANADIAN_DOLLAR -> 1
    }