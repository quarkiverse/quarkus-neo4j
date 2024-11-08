package io.quarkus.neo4j.deployment;

import org.neo4j.driver.Driver;

import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Feature;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.deployment.metrics.MetricsFactoryConsumerBuildItem;
import io.quarkus.neo4j.runtime.Neo4jConfiguration;
import io.quarkus.neo4j.runtime.Neo4jDriverRecorder;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;

class Neo4jDriverProcessor {

    @BuildStep
    FeatureBuildItem createFeature(BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport) {

        // Indicates that this extension would like the SSL support to be enabled
        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(Feature.NEO4J));

        return new FeatureBuildItem(Feature.NEO4J);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    Neo4jDriverBuildItem configureDriverProducer(Neo4jDriverRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            Neo4jConfiguration configuration,
            ShutdownContextBuildItem shutdownContext) {

        RuntimeValue<Driver> driverHolder = recorder.initializeDriver(configuration, shutdownContext);
        syntheticBeans
                .produce(SyntheticBeanBuildItem.configure(Driver.class).runtimeValue(driverHolder).setRuntimeInit().done());

        return new Neo4jDriverBuildItem(driverHolder);
    }

    @BuildStep
    HealthBuildItem addHealthCheck(Neo4jBuildTimeConfig buildTimeConfig) {
        return new HealthBuildItem("io.quarkus.neo4j.runtime.health.Neo4jHealthCheck",
                buildTimeConfig.healthEnabled());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void metrics(Neo4jConfiguration configuration,
            Neo4jDriverRecorder recorder,
            BuildProducer<MetricsFactoryConsumerBuildItem> metrics) {
        metrics.produce(new MetricsFactoryConsumerBuildItem(recorder.registerMetrics(configuration)));
    }

    @BuildStep
    RuntimeInitializedPackageBuildItem deferNettySSLToRuntime() {
        return new RuntimeInitializedPackageBuildItem("io.netty.handler.ssl");
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

        classes.produce(new RuntimeInitializedClassBuildItem(
                "org.neo4j.driver.internal.bolt.basicimpl.async.connection.BoltProtocolUtil"));
        classes.produce(new RuntimeInitializedClassBuildItem(
                "org.neo4j.driver.internal.bolt.basicimpl.async.connection.ChannelAttributes"));
        classes.produce(
                new RuntimeInitializedClassBuildItem(
                        "org.neo4j.driver.internal.bolt.basicimpl.async.connection.ChannelConnectedListener"));

        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.handler.codec.compression.ZstdConstants"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.internal.tcnative.SSL"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.util.AbstractReferenceCounted"));
        classes.produce(new RuntimeInitializedClassBuildItem("io.netty.util.internal.logging.Log4JLogger"));

        // See https://github.com/quarkiverse/quarkus-neo4j/pull/208#issuecomment-1784849182
        classes.produce(new RuntimeInitializedClassBuildItem("io.vertx.core.net.impl.SSLHelper"));
    }
}
