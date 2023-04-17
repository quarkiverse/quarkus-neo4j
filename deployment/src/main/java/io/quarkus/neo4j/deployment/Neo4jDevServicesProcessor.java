package io.quarkus.neo4j.deployment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.jboss.logging.Logger;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.command.InspectContainerResponse;

import io.quarkus.deployment.Feature;
import io.quarkus.deployment.IsDockerWorking;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem.RunningDevService;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.runtime.configuration.ConfigUtils;

class Neo4jDevServicesProcessor {

    private static final Logger log = Logger.getLogger("io.quarkus.neo4j.deployment");

    private static final String NEO4J_URI = "quarkus.neo4j.uri";
    static final String NEO4J_BROWSER_URL = "quarkus.neo4j.browser-url";
    private static final String NEO4J_USER_PROP = "quarkus.neo4j.authentication.username";
    private static final String NEO4J_PASSWORD_PROP = "quarkus.neo4j.authentication.password";

    static volatile RunningDevService devService;
    static volatile Neo4jDevServiceConfig runningConfiguration;
    static volatile boolean first = true;

    private final IsDockerWorking isDockerWorking = new IsDockerWorking(true);

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
    public DevServicesResultBuildItem startNeo4jDevService(
            LaunchModeBuildItem launchMode,
            Neo4jBuildTimeConfig neo4jBuildTimeConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig globalDevServicesConfig) {

        var configuration = new Neo4jDevServiceConfig(neo4jBuildTimeConfig.devservices);

        if (devService != null) {
            if (configuration.equals(runningConfiguration)) {
                return devService.toBuildItem();
            }
            shutdownNeo4j();
            runningConfiguration = null;
        }

        var compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Neo4j Dev Services Starting:", consoleInstalledBuildItem,
                loggingSetupBuildItem);
        try {
            var neo4jContainer = startNeo4j(configuration, globalDevServicesConfig.timeout);
            if (neo4jContainer != null) {
                var config = Map.of(
                        NEO4J_URI, neo4jContainer.getBoltUrl(),
                        NEO4J_BROWSER_URL, neo4jContainer.getBrowserUrl(),
                        NEO4J_PASSWORD_PROP, neo4jContainer.getAdminPassword());
                log.infof("Dev Services started a Neo4j container reachable at %s", neo4jContainer.getBoltUrl());
                log.infof("Neo4j Browser is reachable at %s", neo4jContainer.getBrowserUrl());
                log.infof("The username for both endpoints is `%s`, authenticated by `%s`", "neo4j",
                        neo4jContainer.getAdminPassword());
                log.infof("Connect via Cypher-Shell: cypher-shell -u %s -p %s -a %s", "neo4j",
                        neo4jContainer.getAdminPassword(), neo4jContainer.getBoltUrl());
                devService = new RunningDevService(Feature.NEO4J.getName(), neo4jContainer.getContainerId(),
                        neo4jContainer::close, config);
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownNeo4j();
                    log.info("Dev Services for Neo4j shut down.");
                }
                first = true;
                runningConfiguration = null;
            };
            closeBuildItem.addCloseTask(closeTask, true);
        }
        runningConfiguration = configuration;

        return devService == null ? null : devService.toBuildItem();
    }

    private ExtNeo4jContainer startNeo4j(Neo4jDevServiceConfig configuration, Optional<Duration> timeout) {

        if (!isDockerWorking.getAsBoolean()) {
            log.debug("Not starting Dev Services for Neo4j, as Docker is not working.");
            return null;
        }

        if (!configuration.devServicesEnabled) {
            log.debug("Not starting Dev Services for Neo4j, as it has been disabled in the config.");
            return null;
        }

        // Check if Neo4j URL or password is set to explicitly
        if (ConfigUtils.isPropertyPresent(NEO4J_URI) || ConfigUtils.isPropertyPresent(NEO4J_USER_PROP)
                || ConfigUtils.isPropertyPresent(NEO4J_PASSWORD_PROP)) {
            log.debug("Not starting Dev Services for Neo4j, as there is explicit configuration present.");
            return null;
        }

        var boldIsReachable = Boolean.getBoolean("io.quarkus.neo4j.deployment.devservices.assumeBoltIsReachable")
                || new BoltHandshaker("localhost", 7687).isBoltPortReachable(Duration.ofSeconds(5));
        if (boldIsReachable) {
            log.warn(
                    "Not starting Dev Services for Neo4j, as the default config points to a reachable address. Be aware that your local database will be used.");
            return null;
        }

        var neo4jContainer = ExtNeo4jContainer.of(configuration);
        configuration.additionalEnv.forEach(neo4jContainer::addEnv);
        timeout.ifPresent(neo4jContainer::withStartupTimeout);
        neo4jContainer.start();
        return neo4jContainer;
    }

    private void shutdownNeo4j() {
        if (devService != null) {
            try {
                devService.close();
            } catch (Throwable e) {
                log.error("Failed to stop Neo4j container", e);
            } finally {
                devService = null;
            }
        }
    }

    private final static class ExtNeo4jContainer extends Neo4jContainer<ExtNeo4jContainer> {

        private static final int DEFAULT_HTTP_PORT = 7474;
        private static final int DEFAULT_BOLT_PORT = 7687;

        static ExtNeo4jContainer of(Neo4jDevServiceConfig config) {

            var container = new ExtNeo4jContainer(DockerImageName.parse(config.imageName).asCompatibleSubstituteFor("neo4j"));
            config.fixedBoltPort.ifPresent(port -> container.addFixedExposedPort(port, DEFAULT_BOLT_PORT));
            config.fixedHttpPort.ifPresent(port -> container.addFixedExposedPort(port, DEFAULT_HTTP_PORT));

            var extensionScript = "/neo4j_dev_services_ext.sh";
            return container
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("/io/quarkus/neo4j/deployment" + extensionScript, 0777),
                            extensionScript)
                    .withEnv("EXTENSION_SCRIPT", extensionScript);
        }

        ExtNeo4jContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }

        String getBrowserUrl() {
            return String.format("%s/browser?dbms=bolt://%s@%s:%d", getHttpUrl(), "neo4j", getHost(),
                    getMappedPort(DEFAULT_BOLT_PORT));
        }

        @Override
        protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
            super.containerIsStarting(containerInfo, reused);

            if (reused) {
                return;
            }

            var mappedPort = getMappedPort(DEFAULT_BOLT_PORT);
            var command = String.format("export NEO4J_dbms_connector_bolt_advertised__address=%s:%d\n", getHost(),
                    mappedPort);
            copyFileToContainer(Transferable.of(command.getBytes(StandardCharsets.UTF_8)), "/testcontainers_env");
        }
    }

    private static final class Neo4jDevServiceConfig {
        final boolean devServicesEnabled;
        final String imageName;
        final Map<String, String> additionalEnv;
        final OptionalInt fixedBoltPort;
        final OptionalInt fixedHttpPort;

        Neo4jDevServiceConfig(DevServicesBuildTimeConfig devServicesConfig) {
            this.devServicesEnabled = enabled(devServicesConfig);
            this.imageName = devServicesConfig.imageName;
            this.additionalEnv = new HashMap<>(devServicesConfig.additionalEnv);
            this.fixedBoltPort = devServicesConfig.boltPort;
            this.fixedHttpPort = devServicesConfig.httpPort;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Neo4jDevServiceConfig that = (Neo4jDevServiceConfig) o;
            return devServicesEnabled == that.devServicesEnabled && imageName.equals(that.imageName)
                    && additionalEnv.equals(
                            that.additionalEnv)
                    && fixedBoltPort.equals(that.fixedBoltPort) && fixedHttpPort.equals(
                            that.fixedHttpPort);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, imageName, additionalEnv, fixedBoltPort, fixedHttpPort);
        }
    }

    /**
     * A helper method to encapsulate the {@code Optional<Boolean>} to {code boolean} mapping of that config flag.
     *
     * @param devServicesConfig The configuration of dev services for neo4j
     * @return {@literal true} if Neo4j dev services are enabled or not
     */
    static boolean enabled(DevServicesBuildTimeConfig devServicesConfig) {
        return Optional.ofNullable(devServicesConfig).flatMap(cfg -> cfg.enabled).orElse(true);
    }
}
