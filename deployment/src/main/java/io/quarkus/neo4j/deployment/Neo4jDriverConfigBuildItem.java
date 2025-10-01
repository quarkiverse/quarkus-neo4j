package io.quarkus.neo4j.deployment;

import org.neo4j.driver.Config;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;

public final class Neo4jDriverConfigBuildItem extends SimpleBuildItem {

    private final RuntimeValue<Config> value;

    public Neo4jDriverConfigBuildItem(RuntimeValue<Config> value) {
        this.value = value;
    }

    public RuntimeValue<Config> getValue() {
        return value;
    }
}
