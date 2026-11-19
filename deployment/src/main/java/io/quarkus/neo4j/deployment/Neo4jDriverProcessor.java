package io.quarkus.neo4j.deployment;

import jakarta.inject.Singleton;

import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;

import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.core.Phase;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.metrics.MetricsFactoryConsumerBuildItem;
import io.quarkus.neo4j.runtime.Neo4jConfiguration;
import io.quarkus.neo4j.runtime.Neo4jDriverSupport;
import io.quarkus.neo4j.runtime.Neo4jMetricsBinder;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;

class Neo4jDriverProcessor {

    @BuildStep
    FeatureBuildItem createFeature(BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport) {

        // Indicates that this extension would like the SSL support to be enabled
        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(Feature.NEO4J));

        return new FeatureBuildItem(Feature.NEO4J);
    }

    @BuildStep
    void configureDriver(ServiceRegistrar reg,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<MetricsFactoryConsumerBuildItem> metrics) {

        // The driver is a client connection to an external data system, so it belongs to the DATA phase,
        // which also makes it available to INIT services such as migrations
        reg.forService(Config.class)
                .atPhase(Phase.DATA)
                .require(Neo4jConfiguration.class)
                .onStart((ctx, configuration) -> Neo4jDriverSupport.buildConfig(configuration));

        reg.forService(Driver.class)
                .atPhase(Phase.DATA)
                .require(Neo4jConfiguration.class)
                .require(Config.class)
                // Don't use a method reference here: the service lambda's body is copied into generated code
                // in another package, and a method reference would copy initializeDriver itself, including its
                // calls to package-private helpers.
                .onStart((ctx, configuration, config) -> Neo4jDriverSupport.initializeDriver(ctx, configuration, config));

        // The service value is handed out exactly once, so the bean must not be dependent-scoped
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(Driver.class)
                .scope(Singleton.class)
                .serviceValue(Driver.class)
                .setRuntimeInit()
                .done());

        reg.forService(Neo4jMetricsBinder.class)
                .require(Config.class)
                .onStart((ctx, config) -> Neo4jDriverSupport.registerMetrics(config));
        metrics.produce(new MetricsFactoryConsumerBuildItem(metricsFactoryConsumer(reg)));
    }

    /**
     * The consumers of {@link MetricsFactoryConsumerBuildItem} (i.e. Micrometer) still pass the consumer to their bytecode
     * recorders, so we need to bridge our service to a recorder proxy until that build item is migrated.
     */
    @SuppressWarnings("deprecation")
    private static Neo4jMetricsBinder metricsFactoryConsumer(ServiceRegistrar reg) {
        return reg.getRecorderProxy(Neo4jMetricsBinder.class);
    }

    @BuildStep
    HealthBuildItem addHealthCheck(Neo4jBuildTimeConfig buildTimeConfig) {
        return new HealthBuildItem("io.quarkus.neo4j.runtime.health.Neo4jHealthCheck",
                buildTimeConfig.healthEnabled());
    }

    @BuildStep
    void registerBoltCodecServiceProviders(BuildProducer<ServiceProviderBuildItem> serviceProviders) {
        serviceProviders.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                "org.neo4j.bolt.connection.codec.value_encoding.ValueEncoderFactory"));
        serviceProviders.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                "org.neo4j.bolt.connection.codec.value_encoding.ValueDecoderFactory"));
        serviceProviders.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                "org.neo4j.bolt.connection.codec.packstream.PackStreamEncoderFactory"));
        serviceProviders.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                "org.neo4j.bolt.connection.codec.packstream.PackStreamDecoderFactory"));
    }

    @BuildStep
    RuntimeInitializedPackageBuildItem deferNettySSLToRuntime() {
        return new RuntimeInitializedPackageBuildItem("io.netty.handler.ssl");
    }

    @BuildStep
    RuntimeInitializedPackageBuildItem deferBoltConnectionImplToRuntime() {
        return new RuntimeInitializedPackageBuildItem("org.neo4j.bolt.connection.netty.impl.async.connection");
    }

    @BuildStep
    void deferMiscellaneousClassesToRuntime(BuildProducer<RuntimeInitializedClassBuildItem> classes) {

        // Those are the ones we use, there are more in the package and if we move the whole package some Quarkus stuff
        // would need to be deferred too
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.AbstractReferenceCountedByteBuf"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.ByteBufAllocator"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.ByteBufUtil"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.ByteBufUtil$HexUtil"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.PooledByteBufAllocator"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.UnpooledHeapByteBuf"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.buffer.UnreleasableByteBuf"));

        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.handler.codec.compression.ZstdConstants"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.internal.tcnative.SSL"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.util.AbstractReferenceCounted"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.util.internal.logging.Log4JLogger"));

        // See https://github.com/quarkiverse/quarkus-neo4j/pull/208#issuecomment-1784849182
        // (it creates io.netty.handler.ssl.ClientAuth instances, it was io.vertx.core.net.impl.SSLHelper in Vert.x 4)
        classes.produce(new RuntimeInitializedClassBuildItem("io.vertx.core.internal.tls.SslContextManager"));
    }
}
