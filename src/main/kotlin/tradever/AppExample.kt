package tradever

import org.reactivestreams.example.unicast.AsyncSubscriber
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.models.market.CandleInterval
import ru.tinkoff.invest.openapi.models.streaming.StreamingEvent
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApiFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger
import kotlin.streams.toList

/**
 * Copied from https://github.com/TinkoffCreditSystems/invest-openapi-java-sdk
 * converted to Kotlin, using Idea
 */

object AppExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val logger = try {
            initLogger()
        } catch (ex: IOException) {
            System.err.println("Failed to initialize log: " + ex.localizedMessage)
            return
        }
        val parameters = try {
            extractParams(args)
        } catch (ex: IllegalArgumentException) {
            logger.log(Level.SEVERE, "Failed to read config file", ex)
            return
        }
        val factory = OkHttpOpenApiFactory(parameters.ssoToken, logger)
        try {
            val api: OpenApi
            logger.info("Starting... ")
            if (parameters.sandboxMode) {
                api = factory.createSandboxOpenApiClient(Executors.newSingleThreadExecutor())
                api.sandboxContext.performRegistration(null).join()
            } else {
                api = factory.createOpenApiClient(Executors.newSingleThreadExecutor())
            }
//            val listener = StreamingApiSubscriber(logger, Executors.newSingleThreadExecutor())
//            api.streamingContext.eventPublisher.subscribe(listener)

            val stocks = api.marketContext.marketStocks.get();
            logger.info("Stocks $stocks")

//            for (i in parameters.tickers.indices) {
//                val ticker = parameters.tickers[i]
//                val candleInterval = parameters.candleIntervals[i]
//                logger.info("Find by ticker = $ticker... ")
//                val instrumentsList = api.marketContext.searchMarketInstrumentsByTicker(ticker).join()
//                val instrumentOpt = instrumentsList.instruments.stream().findFirst()
//                val instrument = if (instrumentOpt.isEmpty) {
//                    logger.severe("There is no instrument for ticker = $ticker")
//                    return
//                } else {
//                    instrumentOpt.get()
//                }
//                logger.info("Getting portfolio currencies...")
//                val portfolioCurrencies = api.portfolioContext.getPortfolioCurrencies(null).join()
//                val portfolioCurrencyOpt = portfolioCurrencies.currencies.stream()
//                    .filter { pc: PortfolioCurrency -> pc.currency == instrument.currency }
//                    .findFirst()
//                if (portfolioCurrencyOpt.isEmpty) {
//                    logger.severe("There is no portfolio currency")
//                    return
//                } else {
//                    val portfolioCurrency = portfolioCurrencyOpt.get()
//                    logger.info("There are ${portfolioCurrency.currency} on ${portfolioCurrency.balance.toPlainString()}")
//                }
//                api.streamingContext.sendRequest(StreamingRequest.subscribeCandle(instrument.figi, candleInterval))
//            }

            initCleanupProcedure(api, logger)
            val result = CompletableFuture<Void>()
            result.join()
            api.close()
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "Oops, something wrong", ex)
        }
    }

    @Throws(IOException::class)
    private fun initLogger(): Logger {
        val logManager = LogManager.getLogManager()
        val classLoader = AppExample::class.java.classLoader
        classLoader.getResourceAsStream("logging.properties").use { input ->
            if (input == null) {
                throw FileNotFoundException()
            }
            Files.createDirectories(Paths.get("./logs"))
            logManager.readConfiguration(input)
        }
        return Logger.getLogger(AppExample::class.java.name)
    }

    // TODO: 02.11.2020 let's rather use config file
    private fun extractParams(args: Array<String>) = TradingParameters.of(
        "token",
        "test", "1min", true)

    private fun initCleanupProcedure(api: OpenApi, logger: Logger) {
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                logger.info("Close connection... ")
                if (!api.hasClosed()) api.close()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Failed to close the connection", e)
            }
        })
    }
}

class TradingParameters(
    val ssoToken: String, val tickers: Array<String>, val candleIntervals: Array<CandleInterval>,
    val sandboxMode: Boolean
) {
    companion object {
        fun of(
            ssoTokenArg: String,
            tickersArg: String,
            candleIntervalsArg: String,
            sandboxModeArg: Boolean
        ): TradingParameters {
            val tickers = tickersArg.split(",").toTypedArray()
            val candleIntervals = candleIntervalsArg.split(",").stream()
                .map { str: String -> parseCandleInterval(str) }
                // I bet you can do it better
                .toList().toTypedArray()
            require(candleIntervals.size == tickers.size) {
                "Wrong number of candle intervals and/or tickers"
            }
            return TradingParameters(ssoTokenArg, tickers, candleIntervals, true)
        }

        private fun parseCandleInterval(str: String): CandleInterval {
            return when (str) {
                "1min" -> CandleInterval.ONE_MIN
                "2min" -> CandleInterval.TWO_MIN
                "3min" -> CandleInterval.THREE_MIN
                "5min" -> CandleInterval.FIVE_MIN
                "10min" -> CandleInterval.TEN_MIN
                else -> throw java.lang.IllegalArgumentException("Corrupted candle interval")
            }
        }
    }
}

class StreamingApiSubscriber(private val logger: Logger, executor: Executor) :
    AsyncSubscriber<StreamingEvent>(executor) {
    override fun whenNext(element: StreamingEvent?): Boolean {
        logger.info("Got new event from Streaming API \n $element")
        return true
    }

}