package tradever

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.tinkoff.invest.openapi.OpenApi
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApiFactory
import tradever.dealer.Dealer
import java.io.FileNotFoundException
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
        val logger = LoggerFactory.initLogger(App::class.java.name)
        val config = AppConfiguration.readConfigFile()
        val factory = OkHttpOpenApiFactory(config.ssoToken, logger)
        val api: OpenApi
        logger.info("Starting... ")
        if (config.sandboxMode) {
            api = factory.createSandboxOpenApiClient(Executors.newSingleThreadExecutor())
            api.sandboxContext.performRegistration(null).join()
        } else {
            api = factory.createOpenApiClient(Executors.newSingleThreadExecutor())
        }

        initCleanupProcedure(api, logger)

        val pricesProperties = config.dealers.getValue("prices")
        runBlocking {
            launch {
                Dealer.createPriceDealer(
                    api,
                    pricesProperties.getValue("pricesFile"),
                    pricesProperties.getValue("currentDir"),
                    pricesProperties.getValue("archiveDir")
                ).run()
            }
        }


        api.close()
    }

    object LoggerFactory {
        fun initLogger(name: String): Logger {
            val logManager = LogManager.getLogManager()
            App::class.java.classLoader.getResourceAsStream("logging.properties").use { input ->
                if (input == null) {
                    throw FileNotFoundException()
                }
                Files.createDirectories(Paths.get("./logs"))
                logManager.readConfiguration(input)
            }
            return Logger.getLogger(name)
        }
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

class AppConfiguration(
    val ssoToken: String,
    val sandboxMode: Boolean,
    val dealers: Map<String, Map<String, String>>
) {
    companion object {
        private val mapper = let {
            val mapper = ObjectMapper(YAMLFactory())
            mapper.registerModule(KotlinModule())
            mapper
        }

        fun readConfigFile(): AppConfiguration =
            Files.newBufferedReader(FileSystems.getDefault().getPath("src/main/resources/application.yml")).use {
                mapper.readValue(it, AppConfiguration::class.java)
            }
    }
}