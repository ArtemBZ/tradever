package tradever

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApiFactory
import tradever.dealer.PricesDealer
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

/**
 * Run tradever (see readme).
 */
object App {
    @JvmStatic
    fun main(args: Array<String>) {
        val logger = try {
            initLogger(App::class.java.name)
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

            initCleanupProcedure(api, logger)
//            val result = CompletableFuture<Void>()
//            result.join()
            val pricesProperties = parameters.dealers.getValue("prices")
            PricesDealer(
                api,
                pricesProperties.getValue("pricesFile"),
                pricesProperties.getValue("currentDir"),
                pricesProperties.getValue("archiveDir")
            ).run()
            api.close()
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "Oops, something wrong", ex)
        }
    }

    @Throws(IOException::class)
    fun initLogger(name: String): Logger {
        val logManager = LogManager.getLogManager()
        val classLoader = App::class.java.classLoader
        classLoader.getResourceAsStream("logging.properties").use { input ->
            if (input == null) {
                throw FileNotFoundException()
            }
            Files.createDirectories(Paths.get("./logs"))
            logManager.readConfiguration(input)
        }
        return Logger.getLogger(name)
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
    val ssoToken: String,
    val tickers: Array<String>,
    val sandboxMode: Boolean,
    val dealers: Map<String, Map<String, String>>
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