package io.quarkus.neo4j.runtime;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.internal.Scheme;
import org.neo4j.driver.observation.metrics.ConnectionPoolMetrics;
import org.neo4j.driver.observation.metrics.Metrics;
import org.neo4j.driver.observation.metrics.MetricsObservationProvider;

import io.quarkus.bootstrap.graal.ImageInfo;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.quarkus.runtime.ssl.SslContextConfiguration;

@Recorder
public class Neo4jDriverRecorder {

    private static final Logger log = Logger.getLogger(Neo4jDriverRecorder.class);

    private static final boolean HAS_DRIVER_METRICS;

    static {
        boolean metricsObservationProviderFound = true;
        try {
            Class.forName("org.neo4j.driver.observation.metrics.MetricsObservationProvider", false,
                    Neo4jDriverRecorder.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            metricsObservationProviderFound = false;
        }
        HAS_DRIVER_METRICS = metricsObservationProviderFound;
    }

    private final RuntimeValue<Neo4jConfiguration> configuration;

    public Neo4jDriverRecorder(RuntimeValue<Neo4jConfiguration> configuration) {
        this.configuration = configuration;
    }

    public RuntimeValue<Config> buildConfig() {
        Config.ConfigBuilder configBuilder = createBaseConfig();
        configureSsl(configBuilder, configuration.getValue());
        configurePoolSettings(configBuilder, configuration.getValue().pool());
        configBuilder.withMaxTransactionRetryTime(configuration.getValue().maxTransactionRetryTime().toMillis(),
                TimeUnit.MILLISECONDS);

        return new RuntimeValue<>(configBuilder.build());
    }

    public RuntimeValue<Driver> initializeDriver(ShutdownContext shutdownContext, RuntimeValue<Config> config) {

        String uri = configuration.getValue().uri();
        AuthToken authToken = getAuthToken(configuration.getValue());

        Driver driver = GraphDatabase.driver(uri, authToken, config.getValue());
        shutdownContext.addShutdownTask(driver::close);
        return new RuntimeValue<>(driver);
    }

    static AuthToken getAuthToken(Neo4jConfiguration configuration) {
        if (configuration.authentication().disabled()) {
            return AuthTokens.none();
        }
        return configuration.authentication().value()
                .map(Neo4jDriverRecorder::toAuthToken)
                .orElseGet(
                        () -> AuthTokens.basic(configuration.authentication().username(),
                                configuration.authentication().password()));
    }

    static AuthToken toAuthToken(String value) {

        var idx = value.indexOf("/");
        if (idx < 1 || idx == value.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid value for NEO4J_AUTH, the only supported format is <username>/<password>, neither username nor password are optional");
        }

        return AuthTokens.basic(value.substring(0, idx), value.substring(idx + 1));
    }

    public Consumer<MetricsFactory> registerMetrics(RuntimeValue<Config> config) {

        if (!HAS_DRIVER_METRICS) {
            return metricsFactory -> {
            };
        }

        return config.getValue().observationProvider()
                .filter(MetricsObservationProvider.class::isInstance)
                .map(MetricsObservationProvider.class::cast)
                .map(MetricsObservationProvider::metrics)
                .map(Metrics::connectionPoolMetrics)
                .map(driverMetrics -> (Consumer<MetricsFactory>) metricsFactory -> {
                    var connectionPoolMetrics = driverMetrics.stream().findFirst();
                    // if the pool hasn't been used yet, the ConnectionPoolMetrics object doesn't exist, so use zeros instead
                    metricsFactory.builder("neo4j.acquired").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::acquired).orElse(0L));
                    metricsFactory.builder("neo4j.acquiring").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::acquiring).orElse(0));
                    metricsFactory.builder("neo4j.closed").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::closed).orElse(0L));
                    metricsFactory.builder("neo4j.created").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::created).orElse(0L));
                    metricsFactory.builder("neo4j.creating").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::creating).orElse(0));
                    metricsFactory.builder("neo4j.failedToCreate").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::failedToCreate).orElse(0L));
                    metricsFactory.builder("neo4j.timedOutToAcquire").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::timedOutToAcquire).orElse(0L));
                    metricsFactory.builder("neo4j.totalAcquisitionTime").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::totalAcquisitionTime).orElse(0L));
                    metricsFactory.builder("neo4j.totalConnectionTime").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::totalConnectionTime).orElse(0L));
                    metricsFactory.builder("neo4j.totalInUseCount").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::totalInUseCount).orElse(0L));
                    metricsFactory.builder("neo4j.totalInUseTime").buildCounter(
                            () -> connectionPoolMetrics.map(ConnectionPoolMetrics::totalInUseCount).orElse(0L));
                }).orElseGet(() -> metricsFactory -> {
                });
    }

    private static Config.ConfigBuilder createBaseConfig() {
        return Config.builder();
    }

    private static void configureSsl(Config.ConfigBuilder configBuilder, Neo4jConfiguration configuration) {

        var uri = URI.create(configuration.uri());
        var scheme = uri.getScheme();

        boolean isSecurityScheme = Scheme.isSecurityScheme(scheme);
        boolean isNativeWithoutSslSupport = ImageInfo.inImageRuntimeCode() && !SslContextConfiguration.isSslNativeEnabled();

        // If the URL indicates a secure / security scheme
        // (either neo4j+s (encrypted with full cert checks) or neo4j+ssc (encrypted, but allows self signed certs)
        // we cannot configure security settings again, so we check only if Quarkus can provide the necessary runtime
        if (isSecurityScheme) {
            if (isNativeWithoutSslSupport) {
                throw new ConfigurationException("You cannot use " + scheme
                        + " because SSL support is not available in your current native image setup.",
                        Set.of("quarkus.neo4j.uri"));
            }

            // Nothing to configure
            return;
        }

        // Disable encryption regardless of user configuration when ssl is not natively enabled.
        if (isNativeWithoutSslSupport) {
            log.warn(
                    "Native SSL is disabled, communication between this client and the Neo4j server cannot be encrypted.");
            configBuilder.withoutEncryption();
        } else {
            if (configuration.encrypted()) {
                configBuilder.withEncryption();
                configBuilder.withTrustStrategy(configuration.trustSettings().toInternalRepresentation());
            } else {
                configBuilder.withoutEncryption();
            }
        }
    }

    private static void configurePoolSettings(Config.ConfigBuilder configBuilder, Neo4jConfiguration.Pool pool) {

        if (log.isDebugEnabled()) {
            log.debug("Configuring Neo4j pool settings with " + pool);
        }

        if (pool.logLeakedSessions()) {
            configBuilder.withLeakedSessionsLogging();
        }

        configBuilder.withMaxConnectionPoolSize(pool.maxConnectionPoolSize());
        configBuilder.withConnectionLivenessCheckTimeout(pool.idleTimeBeforeConnectionTest().toMillis(), MILLISECONDS);
        configBuilder.withMaxConnectionLifetime(pool.maxConnectionLifetime().toMillis(), MILLISECONDS);
        configBuilder.withConnectionAcquisitionTimeout(pool.connectionAcquisitionTimeout().toMillis(), MILLISECONDS);

        if (pool.metricsEnabled()) {
            if (!HAS_DRIVER_METRICS) {
                log.warn(
                        "Driver metrics enabled, but module org.neo4j.driver:neo4j-java-driver-observation-metrics not provided");
            } else {
                configBuilder.withObservationProvider(MetricsObservationProvider.newInstance());
            }
        }
    }
}
