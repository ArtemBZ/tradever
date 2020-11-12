package tradever

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.reactivestreams.example.unicast.AsyncSubscriber
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.models.market.CandleInterval
import ru.tinkoff.invest.openapi.models.portfolio.PortfolioCurrencies
import ru.tinkoff.invest.openapi.models.streaming.StreamingEvent
import ru.tinkoff.invest.openapi.models.streaming.StreamingRequest
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApiFactory
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

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
        val parameters = TradingParameters.readConfigFile()
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
            val listener = StreamingApiSubscriber(logger, Executors.newSingleThreadExecutor())
            api.streamingContext.eventPublisher.subscribe(listener)

            for (i in parameters.tickers.indices) {
                val ticker = parameters.tickers[i]
                logger.info("Find by ticker = $ticker... ")
                val instrumentsList = api.marketContext.searchMarketInstrumentsByTicker(ticker).join()
                val instrumentOpt = instrumentsList.instruments.stream().findFirst()
                val instrument = if (instrumentOpt.isEmpty) {
                    logger.severe("There is no instrument for ticker = $ticker")
                    return
                } else {
                    instrumentOpt.get()
                }
                logger.info("Getting portfolio currencies...")
                val portfolioCurrencies = api.portfolioContext.getPortfolioCurrencies(null).join()
                val portfolioCurrencyOpt = portfolioCurrencies.currencies.stream()
                    .filter { pc: PortfolioCurrencies.PortfolioCurrency -> pc.currency == instrument.currency }
                    .findFirst()
                if (portfolioCurrencyOpt.isEmpty) {
                    logger.severe("There is no portfolio currency")
                    return
                } else {
                    val portfolioCurrency = portfolioCurrencyOpt.get()
                    logger.info("There are ${portfolioCurrency.currency} on ${portfolioCurrency.balance.toPlainString()}")
                }
                api.streamingContext.sendRequest(
                    StreamingRequest.subscribeCandle(
                        instrument.figi,
                        CandleInterval.ONE_MIN
                    )
                )
            }

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
    val ssoToken: String, val tickers: Array<String>, val sandboxMode: Boolean
) {
    companion object {
        private val mapper = let {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())
            mapper
        }

        fun readConfigFile(): TradingParameters =
            Files.newBufferedReader(FileSystems.getDefault().getPath("src/main/resources/application.yml")).use {
                mapper.readValue(it, TradingParameters::class.java)

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