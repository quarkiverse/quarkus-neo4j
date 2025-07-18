package io.quarkus.neo4j.runtime;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.jboss.logging.Logger;
import org.neo4j.driver.AuthToken;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.ConnectionPoolMetrics;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.internal.Scheme;

import io.quarkus.arc.Arc;
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

    private final RuntimeValue<Neo4jConfiguration> configuration;

    public Neo4jDriverRecorder(RuntimeValue<Neo4jConfiguration> configuration) {
        this.configuration = configuration;
    }

    public RuntimeValue<Driver> initializeDriver(ShutdownContext shutdownContext) {

        String uri = configuration.getValue().uri();
        AuthToken authToken = getAuthToken(configuration.getValue());

        Config.ConfigBuilder configBuilder = createBaseConfig();
        configureSsl(configBuilder, configuration.getValue());
        configurePoolSettings(configBuilder, configuration.getValue().pool());
        configBuilder.withMaxTransactionRetryTime(configuration.getValue().maxTransactionRetryTime().toMillis(),
                TimeUnit.MILLISECONDS);

        Driver driver = GraphDatabase.driver(uri, authToken, configBuilder.build());
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

    public Consumer<MetricsFactory> registerMetrics() {
        if (configuration.getValue().pool() != null && configuration.getValue().pool().metricsEnabled()) {
            return metricsFactory -> {
                // if the pool hasn't been used yet, the ConnectionPoolMetrics object doesn't exist, so use zeros instead
                metricsFactory.builder("neo4j.acquired").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::acquired).orElse(0L));
                metricsFactory.builder("neo4j.acquiring").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::acquiring).orElse(0));
                metricsFactory.builder("neo4j.closed").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::closed).orElse(0L));
                metricsFactory.builder("neo4j.created").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::created).orElse(0L));
                metricsFactory.builder("neo4j.creating").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::creating).orElse(0));
                metricsFactory.builder("neo4j.failedToCreate").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::failedToCreate).orElse(0L));
                metricsFactory.builder("neo4j.timedOutToAcquire").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::timedOutToAcquire).orElse(0L));
                metricsFactory.builder("neo4j.totalAcquisitionTime").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::totalAcquisitionTime).orElse(0L));
                metricsFactory.builder("neo4j.totalConnectionTime").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::totalConnectionTime).orElse(0L));
                metricsFactory.builder("neo4j.totalInUseCount").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::totalInUseCount).orElse(0L));
                metricsFactory.builder("neo4j.totalInUseTime").buildCounter(
                        () -> getConnectionPoolMetrics().map(ConnectionPoolMetrics::totalInUseCount).orElse(0L));
            };
        } else {
            return metricsFactory -> {

            };
        }
    }

    // Until the pool is actually used for the first time, the ConnectionPoolMetrics object does not exist,
    // so this will be populated later
    private static Optional<ConnectionPoolMetrics> connectionPoolMetrics = Optional.empty();

    private synchronized Optional<ConnectionPoolMetrics> getConnectionPoolMetrics() {
        if (!connectionPoolMetrics.isPresent()) {
            connectionPoolMetrics = Arc.container().instance(Driver.class)
                    .get()
                    .metrics()
                    .connectionPoolMetrics()
                    .stream()
                    .findFirst();
        }
        return connectionPoolMetrics;
    }

    private static Config.ConfigBuilder createBaseConfig() {
        Config.ConfigBuilder configBuilder = Config.builder();
        Logging logging;
        try {
            logging = Logging.slf4j();
        } catch (Exception e) {
            logging = Logging.javaUtilLogging(Level.INFO);
        }
        configBuilder.withLogging(logging);
        return configBuilder;
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
            configBuilder.withDriverMetrics();
        } else {
            configBuilder.withoutDriverMetrics();
        }
    }
}
