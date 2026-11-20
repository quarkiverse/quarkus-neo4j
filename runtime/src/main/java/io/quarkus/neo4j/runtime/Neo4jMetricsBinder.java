package io.quarkus.neo4j.runtime;

import java.util.function.Consumer;

import io.quarkus.runtime.metrics.MetricsFactory;

/**
 * Binds the Neo4j driver metrics to a {@link MetricsFactory}. This is a dedicated, non-generic type so that it can be
 * registered as a service.
 */
@FunctionalInterface
public interface Neo4jMetricsBinder extends Consumer<MetricsFactory> {
}
