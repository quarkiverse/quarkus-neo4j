package io.quarkus.neo4j.deployment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.jboss.logging.Logger;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.command.InspectContainerResponse;

import io.quarkus.deployment.Feature;
import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesComposeProjectBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ConfigUtils;

@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
class Neo4jDevServicesProcessor {

    private static final Logger log = Logger.getLogger("io.quarkus.neo4j.deployment");

    private static final String NEO4J_URI = "quarkus.neo4j.uri";
    private static final String NEO4J_USER_PROP = "quarkus.neo4j.authentication.username";
    private static final String NEO4J_PASSWORD_PROP = "quarkus.neo4j.authentication.password";
    static final int DEFAULT_HTTP_PORT = 7474;
    static final int DEFAULT_BOLT_PORT = 7687;

    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-neo4j";

    private static final ContainerLocator CONTAINER_LOCATOR = new ContainerLocator(DEV_SERVICE_LABEL, DEFAULT_BOLT_PORT);

    @BuildStep
    public void startNeo4jContainer(
            LaunchModeBuildItem launchMode,
            DockerStatusBuildItem dockerStatusBuildItem,
            DevServicesComposeProjectBuildItem composeProjectBuildItem,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            Neo4jBuildTimeConfig neo4jBuildTimeConfig,
            BuildProducer<DevServicesResultBuildItem> devServicesResult,
            DevServicesConfig devServicesConfig) {

        var configuration = new Neo4jDevServiceConfig(neo4jBuildTimeConfig.devservices());

        if (!configuration.devServicesEnabled) {
            log.debug("Not starting Dev Services for Neo4j, as it has been disabled in the config.");
            return;
        }

        if (ConfigUtils.isPropertyPresent(NEO4J_URI) || ConfigUtils.isPropertyPresent(NEO4J_USER_PROP)
                || ConfigUtils.isPropertyPresent(NEO4J_PASSWORD_PROP)) {
            log.debug("Not starting Dev Services for Neo4j, as there is explicit configuration present.");
            return;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker isn't working, please configure the Neo4j URI property (quarkus.neo4j.uri).");
            return;
        }

        var boldIsReachable = Boolean.getBoolean("io.quarkus.neo4j.deployment.devservices.assumeBoltIsReachable")
                || new BoltHandshaker("localhost", DEFAULT_BOLT_PORT).isBoltPortReachable(Duration.ofSeconds(5));
        if (boldIsReachable && configuration.fixedBoltPort.orElse(-1) == DEFAULT_BOLT_PORT) {
            log.warn("Not starting Dev Services for Neo4j, as the configuration requests the same fixed bolt port.");
            return;
        }

        boolean useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(devServicesConfig,
                devServicesSharedNetworkBuildItem);

        DevServicesResultBuildItem discovered = discoverRunningService(composeProjectBuildItem, configuration,
                launchMode.getLaunchMode(), useSharedNetwork);
        if (discovered != null) {
            devServicesResult.produce(discovered);
            return;
        }

        Optional<Duration> timeout = devServicesConfig.timeout();
        devServicesResult.produce(DevServicesResultBuildItem.<ExtNeo4jContainer> owned()
                .feature(Feature.NEO4J)
                .serviceName(configuration.serviceName)
                .serviceConfig(configuration)
                .startable(() -> {
                    var container = new ExtNeo4jContainer(
                            DockerImageName.parse(configuration.imageName).asCompatibleSubstituteFor("neo4j"),
                            configuration.fixedBoltPort,
                            configuration.fixedHttpPort,
                            composeProjectBuildItem.getDefaultNetworkId(),
                            useSharedNetwork);
                    if (configuration.shared) {
                        container.withAdminPassword(configuration.sharedPassword);
                    }
                    configuration.additionalEnv.forEach(container::addEnv);
                    timeout.ifPresent(container::withStartupTimeout);
                    return container
                            .withSharedServiceLabel(launchMode.getLaunchMode(), configuration.serviceName);
                })
                .configProvider(Map.of(
                        NEO4J_URI, container -> "bolt://" + container.getHost() + ":" + container.getBoltPort(),
                        NEO4J_PASSWORD_PROP, ExtNeo4jContainer::getAdminPassword,
                        "quarkus.neo4j.devservices.bolt-port",
                        container -> Integer.toString(container.getBoltPort()),
                        "quarkus.neo4j.devservices.http-port",
                        container -> Integer.toString(container.getHttpPort())))
                .postStartHook(container -> {
                    log.infof("Dev Services started a Neo4j container reachable at %s",
                            container.getConnectionInfo());
                    log.infof("Neo4j Browser is reachable at %s", container.getBrowserUrl());
                    log.infof("The username for both endpoints is `%s`, authenticated by `%s`", "neo4j",
                            container.getAdminPassword());
                    log.infof("Connect via Cypher-Shell: cypher-shell -u %s -p %s -a %s", "neo4j",
                            container.getAdminPassword(), container.getConnectionInfo());
                })
                .build());
    }

    private DevServicesResultBuildItem discoverRunningService(
            DevServicesComposeProjectBuildItem composeProjectBuildItem,
            Neo4jDevServiceConfig configuration,
            LaunchMode launchMode,
            boolean useSharedNetwork) {

        return CONTAINER_LOCATOR.locateContainer(configuration.serviceName, configuration.shared, launchMode)
                .or(() -> ComposeLocator.locateContainer(composeProjectBuildItem,
                        List.of(configuration.imageName, "neo4j"),
                        DEFAULT_BOLT_PORT, launchMode, useSharedNetwork))
                .map(containerAddress -> {
                    var boltPort = containerAddress.getPort();
                    return DevServicesResultBuildItem.discovered()
                            .feature(Feature.NEO4J)
                            .containerId(containerAddress.getId())
                            .config(Map.of(
                                    NEO4J_URI,
                                    String.format("bolt://%s", containerAddress.getUrl()),
                                    NEO4J_PASSWORD_PROP, configuration.sharedPassword,
                                    "quarkus.neo4j.devservices.bolt-port", Integer.toString(boltPort)))
                            .build();
                })
                .orElse(null);
    }

    static final class ExtNeo4jContainer extends Neo4jContainer
            implements Startable {

        private final OptionalInt fixedBoltPort;
        private final OptionalInt fixedHttpPort;
        private final boolean useSharedNetwork;
        private final String hostName;

        ExtNeo4jContainer(DockerImageName dockerImageName,
                OptionalInt fixedBoltPort, OptionalInt fixedHttpPort,
                String defaultNetworkId, boolean useSharedNetwork) {
            super(dockerImageName);
            this.fixedBoltPort = fixedBoltPort;
            this.fixedHttpPort = fixedHttpPort;
            this.useSharedNetwork = useSharedNetwork;
            this.hostName = ConfigureUtil.configureNetwork(this, defaultNetworkId, useSharedNetwork, "neo4j");

            var extensionScript = "/neo4j_dev_services_ext.sh";
            withCopyFileToContainer(
                    MountableFile.forClasspathResource("/io/quarkus/neo4j/deployment" + extensionScript, 0777),
                    extensionScript);
            withEnv("EXTENSION_SCRIPT", extensionScript);
        }

        public ExtNeo4jContainer withSharedServiceLabel(LaunchMode launchMode, String serviceName) {
            if (ConfigureUtil.shouldConfigureSharedServiceLabel(launchMode)) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
            return this;
        }

        @Override
        protected void configure() {
            super.configure();
            if (useSharedNetwork) {
                return;
            }
            fixedBoltPort.ifPresent(port -> addFixedExposedPort(port, DEFAULT_BOLT_PORT));
            fixedHttpPort.ifPresent(port -> addFixedExposedPort(port, DEFAULT_HTTP_PORT));
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

        public int getBoltPort() {
            if (useSharedNetwork) {
                return DEFAULT_BOLT_PORT;
            }
            return getMappedPort(DEFAULT_BOLT_PORT);
        }

        public int getHttpPort() {
            if (useSharedNetwork) {
                return DEFAULT_HTTP_PORT;
            }
            return getMappedPort(DEFAULT_HTTP_PORT);
        }

        @Override
        public String getHost() {
            return useSharedNetwork ? hostName : super.getHost();
        }

        String getBrowserUrl() {
            return String.format("http://%s:%d/browser?dbms=bolt://%s@%s:%d",
                    getHost(), getHttpPort(), "neo4j", getHost(), getBoltPort());
        }

        @Override
        public String getConnectionInfo() {
            return "bolt://" + getHost() + ":" + getBoltPort();
        }

        @Override
        public void close() {
            super.close();
        }
    }

    private record Neo4jDevServiceConfig(
            boolean devServicesEnabled,
            String imageName,
            Map<String, String> additionalEnv,
            OptionalInt fixedBoltPort,
            OptionalInt fixedHttpPort,
            boolean shared,
            String serviceName,
            String sharedPassword) {

        private Neo4jDevServiceConfig {
            additionalEnv = additionalEnv == null ? Map.of() : Map.copyOf(additionalEnv);
        }

        Neo4jDevServiceConfig(DevServicesBuildTimeConfig devServicesConfig) {
            this(Optional.ofNullable(devServicesConfig).flatMap(DevServicesBuildTimeConfig::enabled).orElse(true),
                    devServicesConfig.imageName(), devServicesConfig.additionalEnv(),
                    devServicesConfig.boltPort(), devServicesConfig.httpPort(), devServicesConfig.shared(),
                    devServicesConfig.serviceName(), devServicesConfig.sharedPassword());
        }
    }
}
