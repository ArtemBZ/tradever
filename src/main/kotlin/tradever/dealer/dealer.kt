package tradever.dealer

import ru.tinkoff.invest.openapi.OpenApi

/**
 * Service to run trading strategy that include multiple steps, e.g. buy and sell.
 */
interface Dealer {
    companion object {
        fun createPriceDealer(api: OpenApi, configPath: String, currentDir: String, archiveDir: String) =
            PricesDealer(api, configPath, currentDir, archiveDir)
    }

    suspend fun run()
}