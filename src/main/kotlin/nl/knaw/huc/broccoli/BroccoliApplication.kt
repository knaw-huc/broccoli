package nl.knaw.huc.broccoli

import `in`.vectorpro.dropwizard.swagger.SwaggerBundle
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Application
import io.dropwizard.client.JerseyClientBuilder
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.Constants.APP_NAME
import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.resources.RepublicResource
import nl.knaw.huc.broccoli.service.anno.CachingAnnoRepo
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo
import nl.knaw.huc.broccoli.service.mock.MockIIIFStore
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.servlets.CrossOriginFilter.*
import org.glassfish.jersey.client.ClientProperties.CONNECT_TIMEOUT
import org.glassfish.jersey.client.ClientProperties.READ_TIMEOUT
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import javax.servlet.DispatcherType

class BroccoliApplication : Application<BroccoliConfiguration>() {
    private val log = LoggerFactory.getLogger(javaClass)

    private val appVersion = javaClass.`package`.implementationVersion

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

        val client = JerseyClientBuilder(environment)
            .using(configuration.jerseyClient)
            .build(name)

        client.property(READ_TIMEOUT, 0)
        client.property(CONNECT_TIMEOUT, 0)
        log.info("client.readTimeout (after setting): ${client.configuration.getProperty(READ_TIMEOUT)}")
        log.info("client.connectTimeout (after setting): ${client.configuration.getProperty(CONNECT_TIMEOUT)}")

        log.info("using AnnoRepo located at: ${configuration.annoRepo.uri}, api-key=${configuration.annoRepo.key}")
        log.info("using IIIFRepo located at: ${configuration.iiifUri}")
        log.info("using TextRepo located at: ${configuration.textUri}")

        val naPrefix = "NL-HaNA"
        val naArchiefNr = configuration.republic.archiefNr
        val naInvNr = configuration.republic.volumes[0].invNr
        val opening = "%04d".format(configuration.republic.defaultOpening)
        log.info("sample republic id: urn:republic:${naPrefix}_${naArchiefNr}_${naInvNr}_${opening}")

        val annoRepo = CachingAnnoRepo(FetchingAnnoRepo(configuration.annoRepo, configuration.republic))
        val arc = createAnnoRepoClient(configuration.annoRepo)
        environment.jersey().apply {
            register(AboutResource(configuration, name, appVersion))
            register(HomePageResource())
            register(RepublicResource(configuration, annoRepo, MockIIIFStore(), client))
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

    private fun createAnnoRepoClient(config: AnnoRepoConfiguration): AnnoRepoClient {
        return AnnoRepoClient(
            serverURI = URI.create(config.uri),
            apiKey = config.key,
            userAgent = "${name} (${javaClass.name}/$appVersion)"
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