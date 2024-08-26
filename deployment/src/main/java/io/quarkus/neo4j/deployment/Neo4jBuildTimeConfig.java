package io.quarkus.neo4j.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.neo4j")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface Neo4jBuildTimeConfig {

    /**
     * {@return whether a health check is published in case the smallrye-health extension is present}
     */
    @WithName("health.enabled")
    @WithDefault("true")
    boolean healthEnabled();

    /**
     * DevServices allows Quarkus to automatically start a Neo4j instance in dev and test mode.
     * {@return Configuration for DevServices}
     */
    DevServicesBuildTimeConfig devservices();
}
