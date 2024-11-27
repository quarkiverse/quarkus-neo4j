package io.quarkus.neo4j.deployment;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface DevServicesBuildTimeConfig {

    /**
     * If DevServices has been explicitly enabled or disabled. DevServices is generally enabled
     * by default, unless there is an existing configuration present.
     * When DevServices is enabled Quarkus will attempt to automatically configure and start
     * a database when running in Dev or Test mode.
     *
     * @return whether dev services are enabled or not
     */
    Optional<Boolean> enabled();

    /**
     * {@return the container image name to use, for container based DevServices providers}
     */
    @WithDefault("neo4j:5")
    String imageName();

    /**
     * {@return additional environment entries that can be added to the container before its start}
     */
    Map<String, String> additionalEnv();

    /**
     * This value can be used to specify the port to which the bolt-port of the container is exposed. It must be a free
     * port, otherwise startup will fail. A random, free port will be used by default. Either way, a messsage will be
     * logged on which port the Neo4j container is reachable over bolt.
     * <p>
     * Ignored when container sharing is enabled.
     *
     * @return a specific port to bind the containers bolt-port to
     */
    OptionalInt boltPort();

    /**
     * This value can be used to specify the port to which the http-port of the container is exposed. It must be a free
     * port, otherwise startup will fail. A random, free port will be used by default. Either way, a messsage will be
     * logged on which port the Neo4j Browser is available.
     * <p>
     * Ignored when container sharing is enabled.
     *
     * @return a specific port to bind the containers http-port to
     */
    OptionalInt httpPort();

    /**
     * Indicates if the Neo4j server managed by Quarkus Dev Services is shared.
     * When shared, Quarkus looks for running containers using label-based service discovery.
     * If a matching container is found, it is used, and so a second one is not started.
     * Otherwise, Dev Services for Neo4j starts a new container.
     * <p>
     * The discovery uses the {@code quarkus-dev-service-neo4j} label.
     * The value is configured using the {@code service-name} property.
     * <p>
     * Container sharing is only used in dev mode and disabled by default
     *
     * @return flag to enable container sharing
     */
    @WithDefault("false")
    boolean shared();

    /**
     * The value of the {@code quarkus-dev-service-neo4j} label attached to the started container.
     * This property is used when {@code shared} is set to {@code true}.
     * In this case, before starting a container, Dev Services for Neo4j looks for a container with the
     * {@code quarkus-dev-service-neo4j} label
     * set to the configured value. If found, it will use this container instead of starting a new one. Otherwise, it
     * starts a new container with the {@code quarkus-dev-service-neo4j} label set to the specified value.
     * <p>
     * This property is used when you need multiple shared Neo4j servers.
     */
    @WithDefault("neo4j")
    String serviceName();

    /**
     * This property is used only when you create multiple shared Neo4j servers
     *
     * @return one password for
     */
    @WithDefault("verysecret")
    String sharedPassword();
}
