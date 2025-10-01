package nl.knaw.huc.broccoli

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.ParseContext
import io.dropwizard.assets.AssetsBundle
import io.dropwizard.client.JerseyClientBuilder
import io.dropwizard.client.JerseyClientConfiguration
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.core.Application
import io.dropwizard.core.setup.Bootstrap
import io.dropwizard.core.setup.Environment
import io.dropwizard.jetty.setup.ServletEnvironment
import io.federecio.dropwizard.swagger.SwaggerBundle
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration
import jakarta.servlet.DispatcherType
import jakarta.ws.rs.client.Client
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.Constants.APP_NAME
import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.config.ProjectConfiguration
import nl.knaw.huc.broccoli.config.TextRepoConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.log.RequestTraceLogFilter
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.resources.brinta.BrintaResource
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.cache.LRUCache
import nl.knaw.huc.broccoli.service.text.TextFetcher
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.servlets.CrossOriginFilter.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*

class BroccoliApplication : Application<BroccoliConfiguration>() {
    private val log = LoggerFactory.getLogger(javaClass)

    private val appVersion = javaClass.`package`.implementationVersion ?: "unknown version"

    override fun getName(): String = APP_NAME

    override fun initialize(bootstrap: Bootstrap<BroccoliConfiguration?>) {
        with(bootstrap) {
            objectMapper.registerKotlinModule()
            configurationSourceProvider = SubstitutingSourceProvider(
                configurationSourceProvider,
                EnvironmentVariableSubstitutor()
            )
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
                    + Constants.EnvironmentVariable.entries
                .joinToString("\n") { e ->
                    " ${e.name}:\t${System.getenv(e.name) ?: "(not set, using default)"}"
                } +
                    "\n"
        )

        val client = createClient(configuration.jerseyClient, environment)
        val projects = configureProjects(configuration.projects, client)
        val jsonParser = createJsonParser()
        val objectMapper = createJsonMapper()
        val globalCache = configuration.globalCache?.let { LRUCache<Any, Any>(capacity = it.capacity) }

        with(environment.jersey()) {
            register(AboutResource(configuration, name, appVersion))
            register(HomePageResource())
            register(ProjectsResource(projects, client, jsonParser, objectMapper, globalCache))
            register(BrintaResource(projects, client))
            register(RequestTraceLogFilter())
        }

        setupCORSHeaders(environment.servlets())

        log.info(
            "\n\n  Starting $name (v$appVersion)\n" +
                    "       locally accessible at http://localhost:${System.getenv(Constants.EnvironmentVariable.BR_SERVER_PORT.name) ?: 8080}\n" +
                    "    externally accessible at ${configuration.externalBaseUrl}\n"
        )
    }


    private fun createClient(jerseyClient: JerseyClientConfiguration, environment: Environment): Client {
        return JerseyClientBuilder(environment)
            .using(jerseyClient)
            .build(name)
    }

    private fun configureProjects(
        projectConfigurations: List<ProjectConfiguration>,
        client: Client
    ): Map<String, Project> {
        return projectConfigurations.associate { config ->
            log.info("configuring project: ${config.name}:")
            config.name to Project(
                name = config.name,
                textType = config.textType,
                topTierBodyType = config.topTierBodyType,
                views = config.views.associateBy { view -> view.name },
                brinta = config.brinta,
                textFetcher = createTextRepo(config.textRepo, client),
                annoRepo = createAnnoRepo(config.annoRepo, config.textType)
            )
        }
    }

    private fun createTextRepo(textRepoConfig: TextRepoConfiguration, client: Client) =
        with(textRepoConfig) {
            TextFetcher(client, uri, apiKey)
        }

    fun createAnnoRepo(annoRepoConfig: AnnoRepoConfiguration, textType: String) =
        with(annoRepoConfig) {
            val serverURI = URI.create(uri)
            val userAgent = "$name (${this@BroccoliApplication.javaClass.name}/$appVersion)"
            val client = AnnoRepoClient(serverURI, apiKey, userAgent)
            log.info("- setting up AnnoRepo: uri=$serverURI, container=$containerName, apiKey=$apiKey, userAgent=$userAgent")

            AnnoRepo(client, containerName, textType, cacheCapacity, cacheThreshold)
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

        fun createJsonParser(): ParseContext =
            JsonPath.using(
                Configuration.defaultConfiguration()
                    .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
            )

        fun createJsonMapper() = ObjectMapper()
            .registerKotlinModule()
    }
}
