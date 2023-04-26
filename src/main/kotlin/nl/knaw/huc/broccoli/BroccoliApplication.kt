package nl.knaw.huc.broccoli

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import `in`.vectorpro.dropwizard.swagger.SwaggerBundle
import `in`.vectorpro.dropwizard.swagger.SwaggerBundleConfiguration
import io.dropwizard.Application
import io.dropwizard.assets.AssetsBundle
import io.dropwizard.client.JerseyClientBuilder
import io.dropwizard.client.JerseyClientConfiguration
import io.dropwizard.configuration.EnvironmentVariableSubstitutor
import io.dropwizard.configuration.SubstitutingSourceProvider
import io.dropwizard.jetty.setup.ServletEnvironment
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.Constants.APP_NAME
import nl.knaw.huc.broccoli.config.*
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.resources.brinta.BrintaResource
import nl.knaw.huc.broccoli.resources.globalise.GlobaliseResource
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource
import nl.knaw.huc.broccoli.resources.republic.RepublicResource
import nl.knaw.huc.broccoli.resources.republic.RepublicVolumeMapper
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.mock.MockIIIFStore
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.servlets.CrossOriginFilter.*
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*
import javax.servlet.DispatcherType
import javax.ws.rs.client.Client

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

        val projects = configureProjects(configuration.projects)

        val client = createClient(configuration.jerseyClient, environment)

        val jsonParser =
            JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL))

        with(environment.jersey()) {
            register(AboutResource(configuration, name, appVersion))
            register(HomePageResource())
            register(ProjectsResource(projects, client, jsonParser))
            register(BrintaResource(projects, client, jsonParser))
        }

        registerLegacyResources(configuration, projects, client, environment)
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
        /*
        .also {
            log.info("client.readTimeout (before setting): ${it.configuration.getProperty(READ_TIMEOUT)}")
            it.property(READ_TIMEOUT, 0)
            log.info("client.readTimeout (after setting): ${it.configuration.getProperty(READ_TIMEOUT)}")

            log.info("client.connectTimeout (before setting): ${it.configuration.getProperty(CONNECT_TIMEOUT)}")
            it.property(CONNECT_TIMEOUT, 0)
            log.info("client.connectTimeout (after setting): ${it.configuration.getProperty(CONNECT_TIMEOUT)}")
        }
         */
    }

    private fun configureProjects(projectConfigurations: List<ProjectConfiguration>): Map<String, Project> {
        return projectConfigurations.associate {
            log.info("configuring project: ${it.name}:")
            it.name to Project(
                name = it.name,
                tiers = it.tiers,
                brinta = it.brinta,
                textRepo = createTextRepo(it.textRepo),
                annoRepo = createAnnoRepo(it.annoRepo)
            )
        }
    }

    private fun createTextRepo(textRepoConfig: TextRepoConfiguration) =
        with(textRepoConfig) {
            TextRepo(uri, apiKey)
        }

    private fun createAnnoRepo(annoRepoConfig: AnnoRepoConfiguration) =
        with(annoRepoConfig) {
            val serverURI = URI.create(uri)
            val userAgent = "$name (${this@BroccoliApplication.javaClass.name}/$appVersion)"
            log.info("- setting up AnnoRepo: uri=$serverURI, container=$containerName, apiKey=$apiKey, userAgent=$userAgent")

            AnnoRepo(AnnoRepoClient(serverURI, apiKey, userAgent), containerName)
        }

    private fun registerLegacyResources(
        configuration: BroccoliConfiguration,
        projects: Map<String, Project>,
        client: Client,
        environment: Environment,
    ) {
        val republicAnnoRepoClient = projects["republic"]!!.annoRepo
        val globaliseAnnoRepoClient = projects["globalise"]!!.annoRepo

        val volumeMapper = RepublicVolumeMapper(configuration.republic)

        val iiifStore = MockIIIFStore(configuration.iiifUri, client)

        with(environment.jersey()) {
            register(GlobaliseResource(configuration.globalise, globaliseAnnoRepoClient, client))
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
