package nl.knaw.huc.broccoli

import `in`.vectorpro.dropwizard.swagger.SwaggerBundle
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Application
import io.dropwizard.assets.AssetsBundle
import io.dropwizard.client.JerseyClientBuilder
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.jetty.setup.ServletEnvironment
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.Constants.APP_NAME
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.resources.globalise.GlobaliseResource
import nl.knaw.huc.broccoli.resources.republic.RepublicResource
import nl.knaw.huc.broccoli.resources.republic.RepublicVolumeMapper
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
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

    private val appVersion = javaClass.`package`.implementationVersion ?: "unknown version"

    override fun getName(): String = APP_NAME

    override fun initialize(bootstrap: Bootstrap<BroccoliConfiguration?>) {
        bootstrap.configurationSourceProvider = SubstitutingSourceProvider(
            bootstrap.configurationSourceProvider, EnvironmentVariableSubstitutor()
        )
        with(bootstrap) {
            addBundle(getSwaggerBundle())
            addBundle(AssetsBundle("/mock"))
        }
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

        log.info("registered projects: ")
        log.info(" +- globalise: ${configuration.globalise.annoRepo.uri}")
        log.info(" +- republic: ${configuration.republic.annoRepo.uri}")
        log.info("using IIIFRepo located at: ${configuration.iiifUri}")
        log.info("using TextRepo located at: ${configuration.textUri}")

        registerResources(configuration, environment)
        setupCORSHeaders(environment.servlets())

        log.info(
            "\n\n  Starting $name (v$appVersion)\n" +
                    "       locally accessible at http://localhost:${System.getenv(Constants.EnvironmentVariable.BR_SERVER_PORT.name) ?: 8080}\n" +
                    "    externally accessible at ${configuration.externalBaseUrl}\n"
        )
    }

    private fun registerResources(configuration: BroccoliConfiguration, environment: Environment) {
        val client = JerseyClientBuilder(environment)
            .using(configuration.jerseyClient)
            .build(name)

        client.property(READ_TIMEOUT, 0)
        client.property(CONNECT_TIMEOUT, 0)
        log.info("client.readTimeout (after setting): ${client.configuration.getProperty(READ_TIMEOUT)}")
        log.info("client.connectTimeout (after setting): ${client.configuration.getProperty(CONNECT_TIMEOUT)}")
        val republicAnnoRepoClient = configuration.republic.annoRepo.run {
            AnnoRepo(
                AnnoRepoClient(
                    serverURI = URI.create(uri),
                    apiKey = apiKey,
                    userAgent = "$name (${this@BroccoliApplication.javaClass.name}/$appVersion)"
                ), containerName
            )
        }

        val volumeMapper = RepublicVolumeMapper(configuration.republic)

        val iiifStore = MockIIIFStore(configuration.iiifUri, client)

        with(environment.jersey()) {
            register(AboutResource(configuration, name, appVersion))
            register(HomePageResource())
            register(GlobaliseResource(configuration.globalise))
            register(RepublicResource(configuration.republic, volumeMapper, republicAnnoRepoClient, iiifStore, client))
        }
    }

    private fun setupCORSHeaders(environment: ServletEnvironment) {
        // Enable CORS headers
        val corsFilter = environment.addFilter("CORS", CrossOriginFilter::class.java)

        // Configure CORS parameters
        corsFilter.setInitParameter(ALLOWED_ORIGINS_PARAM, "*")
        corsFilter.setInitParameter(ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin")
        corsFilter.setInitParameter(ALLOWED_METHODS_PARAM, "OPTIONS,GET,PUT,POST,DELETE,HEAD")

        // Add URL mapping
        corsFilter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType::class.java), true, "/*")
    }

    companion object {
        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            BroccoliApplication().run(*args)
        }
    }
}
