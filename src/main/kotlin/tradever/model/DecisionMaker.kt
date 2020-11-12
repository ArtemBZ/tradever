package tradever.model

fun canSold(previous: Double, current: Double) =
    when {
        current - previous > 0.5 -> true
        else -> false
    }