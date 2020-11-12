package tradever.collector

fun parseNumber(value: String): Int? {
    val number = try {
        Integer.parseInt(value)
    } catch (e: NumberFormatException) {
        null
    }
    return number // doesn't make sense
}