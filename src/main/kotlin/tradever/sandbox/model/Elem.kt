package tradever.sandbox.model

import tradever.sandbox.model.Currency.*

// value object
// public by default
// it has different name
class Element(val currentPrice: Double, var name: String) {
    val isCompleted: Boolean
        get() = false
}

enum class Currency(val code: String, val label: String) {
    US_DOLLAR("USD", "$"),
    EURO("EUR", "€"),
    RUBLE("RUB", "₽"); // ';' !!!

    fun getSupportedCurrencies() = US_DOLLAR.code + RUBLE.code
}

fun getMinValue(currency: Currency) =
    // you can use set as well
    when (currency) {
        RUBLE -> 10
        US_DOLLAR, EURO -> 1
    }