package tradever.dealer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.*
import org.reactivestreams.example.unicast.AsyncSubscriber
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.models.market.CandleInterval
import ru.tinkoff.invest.openapi.models.streaming.StreamingEvent
import ru.tinkoff.invest.openapi.models.streaming.StreamingEvent.Candle
import ru.tinkoff.invest.openapi.models.streaming.StreamingRequest
import tradever.dealer.Step.*
import java.io.File
import java.math.BigDecimal
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.logging.Logger
import java.util.stream.Collectors


private enum class Step {
    NOT_STARTED, BUY, SELL, PROFIT
}

private val operationsInterval = CandleInterval.ONE_MIN

/**
 * The simplest implementation of dealer.
 * It acts according to config file that has self-described schema.
 */
class PricesDealer(
    private val api: OpenApi,
    private val configPath: String,
    private val currentDir: String,
    private val archiveDir: String
) : Dealer {

    companion object {
        private val mapper = let {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())
            mapper
        }
        private val LOG: Logger = Logger.getLogger(PricesDealer.toString())
    }

    override suspend fun run() = coroutineScope {
        for (tradeProperty in readTradeProperties()) {
            launch {
                try {
                    var stepAndFile = getCurrentStep(tradeProperty.value.figi)

                    while (stepAndFile.first != PROFIT) {
                        stepAndFile = when (stepAndFile.first) {
                            NOT_STARTED -> startDeal(tradeProperty.value.figi, stepAndFile.second)
                            BUY -> buy(
                                tradeProperty.key,
                                tradeProperty.value.figi,
                                tradeProperty.value.priceToBuy,
                                stepAndFile.second
                            )

                            SELL -> sell(
                                tradeProperty.key,
                                tradeProperty.value.figi,
                                tradeProperty.value.priceToSell,
                                stepAndFile.second
                            )

                            else -> throw java.lang.IllegalStateException("Undefined next step for $tradeProperty.key")
                        }
                    }
                    profit(tradeProperty.value.figi, stepAndFile.second)
                } catch (e: Exception) {
                    LOG.warning("Error occurred with deal for ${tradeProperty.key}: $e")
                }
            }
        }
        LOG.info("Run all")
    }

    private fun getCurrentStep(figi: String): Pair<Step, File> {
        val files =
            Files.find(Path.of(currentDir), 1, { path, _ -> path.toFile().name.startsWith("${figi}_") })
                .collect(Collectors.toList())
        if (files.size > 1) {
            throw IllegalStateException("Failed to identify current step for $figi. Found ${files.size} files in $currentDir")
        }
        if (files.size == 0) {
            return NOT_STARTED to Path.of(currentDir, "${figi}_${BUY}").toFile() // destructuring declaration
        }

        return valueOf(files[0].fileName.toString().removePrefix("${figi}_")) to
                Path.of(currentDir, files[0].fileName.toString()).toFile()
    }

    private fun readTradeProperties(): Map<String, TradeProperties> {
        val props: Map<String, Map<String, String>> =
            Files.newBufferedReader(FileSystems.getDefault().getPath(configPath)).use {
                mapper.readValue(it, object : TypeReference<Map<String, Map<String, String>>>() {})
            }
        val result = hashMapOf<String, TradeProperties>()
        for (figiElem in props) {
            val properties =
                TradeProperties(figiElem.value.getValue("buy").toDouble(), figiElem.value.getValue("sell").toDouble())
            properties.figi = figiElem.value.getValue("name")

            result[figiElem.key] = properties
        }
        return result
    }

    private fun startDeal(figi: String, file: File): Pair<Step, File> {
        LOG.info("Let's do that! Starting trading for $figi")
        file.createNewFile()
        return BUY to file
    }

    private fun profit(name: String, file: File) {
        LOG.info("Profit! $name")
        file.appendText("Profit = TODO")
    }

    private suspend fun sell(figi: String, name: String, priceToSell: Double, file: File): Pair<Step, File> {
        val listener = CandleSubscriber(LOG, Executors.newSingleThreadExecutor(), file,
            action = { candle ->
                if (candle.openPrice > BigDecimal.valueOf(priceToSell)) {
                    file.appendText(
                        """Got price above target: $priceToSell
                    |""".trimMargin()
                    )
                    LOG.info("It's time to sell $name, current price = ${candle.openPrice}; target = $priceToSell")
                    false
                } else {
                    true
                }
            })

        waitWhenListenerCompleted(listener, figi)

        sell(figi, name, file)

        val profitFile =
            Path.of(
                archiveDir, "${name}_${PROFIT}_" +
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "_")
            )
                .toFile()
        withContext(Dispatchers.IO) {
            Files.move(file.toPath(), profitFile.toPath())
        }
        return PROFIT to profitFile
    }

    private fun sell(figi: String, name: String, file: File) {
        LOG.info("Stub for sell $name($figi)")
        file.appendText("Sold with TODO price \n")
    }

    private suspend fun buy(figi: String, name: String, priceToBuy: Double, file: File): Pair<Step, File> {
        val listener = CandleSubscriber(
            LOG, Executors.newSingleThreadExecutor(), file
        ) { candle ->
            if (candle.openPrice < BigDecimal.valueOf(priceToBuy)) {
                file.appendText(
                    """Got price bellow target: $priceToBuy
                    |""".trimMargin()
                )
                LOG.info("It's time to buy $name, current price = ${candle.openPrice}; target = $priceToBuy")
                false
            } else {
                true
            }
        }

        waitWhenListenerCompleted(listener, figi)

        buy(figi, name, file)

        val sellFile = Path.of(currentDir, "${name}_${SELL}").toFile()
        withContext(Dispatchers.IO) {
            Files.move(file.toPath(), sellFile.toPath())
        }
        return SELL to sellFile
    }

    private suspend fun waitWhenListenerCompleted(listener: CandleSubscriber, figi: String) {
        with(api.streamingContext) {
            eventPublisher.subscribe(listener)

            sendRequest(
                StreamingRequest.subscribeCandle(
                    figi,
                    operationsInterval
                )
            )

            while (!listener.isCompleted) {
                delay(2000)
            }

            sendRequest(
                StreamingRequest.unsubscribeCandle(
                    figi,
                    operationsInterval
                )
            )
        }

    }

    private fun buy(figi: String, name: String, file: File) {
        LOG.info("Stub for buy $name")
        file.appendText("Bought $name ($figi) with TODO price \n")
    }
}

private data class TradeProperties(val priceToBuy: Double, val priceToSell: Double) {
    // just to demonstrate
    var figi: String = "undefined"
        get() = field
        set(value) {
            field = value
        }

    override fun toString(): String {
        return super.toString()
    }
}

private class CandleSubscriber(
    private val logger: Logger,
    executor: Executor,
    private val file: File,
    private val action: (Candle) -> Boolean
) : AsyncSubscriber<StreamingEvent>(executor) {
    var isCompleted = false

    override fun whenNext(element: StreamingEvent?): Boolean {
        logger.info("Got new event from Streaming API \n $element")
        val candle = element as? Candle ?: return false
        file.appendText(candle.openPrice.toString() + "\n")
        isCompleted = !action(candle)
        return !isCompleted
    }
}