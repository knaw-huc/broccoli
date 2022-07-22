package nl.knaw.huc.broccoli

import `in`.vectorpro.dropwizard.swagger.SwaggerBundle
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Application
import io.dropwizard.client.JerseyClientBuilder
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.Constants.APP_NAME
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.resources.RepublicResource
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.ResourceLoader
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.servlets.CrossOriginFilter.*
import org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT
import org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT
import org.slf4j.LoggerFactory
import java.util.*
import javax.servlet.DispatcherType


class BroccoliApplication : Application<BroccoliConfiguration>() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName(): String = APP_NAME

    override fun initialize(bootstrap: Bootstrap<BroccoliConfiguration?>) {
        bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
            bootstrap.configurationSourceProvider, EnvironmentVariableSubstitutor()
        )
        bootstrap.addBundle(getSwaggerBundle())
    }

    private fun getSwaggerBundle() = object : SwaggerBundle<BroccoliConfiguration>() {
        override fun getSwaggerBundleConfiguration(configuration: BroccoliConfiguration): SwaggerBundleConfiguration =
            configuration.swaggerBundleConfiguration
    }

    override fun run(configuration: BroccoliConfiguration, environment: Environment) {
        log.info(
            "BR_ environment variables:\n\n"
                    + Constants.EnvironmentVariable.values()
                .joinToString("\n") { e ->
                    " ${e.name}:\t${System.getenv(e.name) ?: "(not set, using default)"}"
                } +
                    "\n"
        )

        val appVersion = javaClass.getPackage().implementationVersion

        val client = JerseyClientBuilder(environment)
            .using(configuration.jerseyClient)
            .build(name)

        client.property(READ_TIMEOUT, 0)
        client.property(CONNECT_TIMEOUT, 0)
        log.info("client.readTimeout (after setting): ${client.configuration.getProperty(READ_TIMEOUT)}")
        log.info("client.connectTimeout (after setting): ${client.configuration.getProperty(CONNECT_TIMEOUT)}")

        environment.jersey().apply {
            register(AboutResource(configuration, name, appVersion))
            register(HomePageResource())
            val iiifStore = object: IIIFStore {
                override fun getCanvasId(volume: String, opening: Int): String {
                    return ResourceLoader.asText("mock/manifest-1728.json")!!
                }
            }
            register(RepublicResource(configuration, iiifStore, client))
//            register(RuntimeExceptionMapper())
        }

        environment.servlets().apply {
            // Enable CORS headers
            val cors = addFilter("CORS", CrossOriginFilter::class.java)

            // Configure CORS parameters
            cors.setInitParameter(ALLOWED_ORIGINS_PARAM, "*")
            cors.setInitParameter(ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin")
            cors.setInitParameter(ALLOWED_METHODS_PARAM, "OPTIONS,GET,PUT,POST,DELETE,HEAD")

            // Add URL mapping
            cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType::class.java), true, "/*")
        }

        log.info(
            "\n\n  Starting $name (v$appVersion)\n" +
                    "       locally accessible at http://localhost:${System.getenv(Constants.EnvironmentVariable.BR_SERVER_PORT.name) ?: 8080}\n" +
                    "    externally accessible at ${configuration.externalBaseUrl}\n"
        )
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            BroccoliApplication().run(*args)
        }
    }
}