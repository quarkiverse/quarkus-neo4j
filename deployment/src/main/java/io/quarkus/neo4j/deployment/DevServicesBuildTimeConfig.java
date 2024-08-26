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
     *
     * @return a specific port to bind the containers bolt-port to
     */
    OptionalInt boltPort();

    /**
     * This value can be used to specify the port to which the http-port of the container is exposed. It must be a free
     * port, otherwise startup will fail. A random, free port will be used by default. Either way, a messsage will be
     * logged on which port the Neo4j Browser is available.
     *
     * @return a specific port to bind the containers http-port to
     */
    OptionalInt httpPort();
}
