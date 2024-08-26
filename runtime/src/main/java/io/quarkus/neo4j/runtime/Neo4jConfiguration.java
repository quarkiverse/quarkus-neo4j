package io.quarkus.neo4j.runtime;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import org.neo4j.driver.Config;

import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.neo4j")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface Neo4jConfiguration {

    String DEFAULT_SERVER_URI = "bolt://localhost:7687";
    String DEFAULT_USERNAME = "neo4j";
    String DEFAULT_PASSWORD = "neo4j";

    /**
     * {@return the uri this driver should connect to. The driver supports bolt, bolt+routing or neo4j as schemes}
     */
    @WithDefault(DEFAULT_SERVER_URI)
    String uri();

    /**
     * {@return the authentication}
     */
    @ConfigDocSection
    Authentication authentication();

    /**
     * {@return if the driver should use encrypted traffic}
     */
    @WithDefault("false")
    boolean encrypted();

    /**
     * {@return the trust settings for encrypted traffic}
     */
    @ConfigDocSection
    TrustSettings trustSettings();

    /**
     * {@return the maximum time transactions are allowed to retry}
     */
    @WithDefault("30S")
    Duration maxTransactionRetryTime();

    /**
     * {@return the connection pool}
     */
    @ConfigDocSection
    Pool pool();

    @ConfigGroup
    interface Authentication {

        /**
         * {@return the login of the user connecting to the database}
         */
        @WithDefault(DEFAULT_USERNAME)
        String username();

        /**
         * {@return the password of the user connecting to the database}
         */
        @WithDefault(DEFAULT_PASSWORD)
        String password();

        /**
         * {@return whether disable authentication or not}
         */
        @WithDefault("false")
        boolean disabled();

        /**
         * An optional field that when is not empty has precedence over {@link #username} and {@link #password}. It behaves
         * the same way as {@literal NEO4J_AUTH} in the official docker image, containing both the username and password
         * separated via a single forward slash ({@code /}).
         *
         * @return a concrete value for the token, overriding all other settings
         */
        Optional<String> value();
    }

    @ConfigGroup
    interface TrustSettings {

        enum Strategy {

            TRUST_ALL_CERTIFICATES,

            TRUST_CUSTOM_CA_SIGNED_CERTIFICATES,

            TRUST_SYSTEM_CA_SIGNED_CERTIFICATES
        }

        /**
         * {@return which trust strategy to apply when using encrypted traffic}
         */
        @WithDefault("TRUST_SYSTEM_CA_SIGNED_CERTIFICATES")
        Strategy strategy();

        /**
         * {@return the file of the certificate to use}
         */
        Optional<Path> certFile();

        /**
         * {@return whether hostname verification is used}
         */
        @WithDefault("false")
        boolean hostnameVerificationEnabled();

        default Config.TrustStrategy toInternalRepresentation() {

            Config.TrustStrategy internalRepresentation;
            Strategy nonNullStrategy = strategy() == null ? Strategy.TRUST_SYSTEM_CA_SIGNED_CERTIFICATES : strategy();
            internalRepresentation = switch (nonNullStrategy) {
                case TRUST_ALL_CERTIFICATES -> Config.TrustStrategy.trustAllCertificates();
                case TRUST_SYSTEM_CA_SIGNED_CERTIFICATES -> Config.TrustStrategy.trustSystemCertificates();
                case TRUST_CUSTOM_CA_SIGNED_CERTIFICATES -> {
                    File certFile = certFile().map(Path::toFile).filter(File::isFile)
                            .orElseThrow(() -> new RuntimeException("Configured trust strategy requires a certificate file."));
                    yield Config.TrustStrategy.trustCustomCertificateSignedBy(certFile);
                }
            };

            if (hostnameVerificationEnabled()) {
                internalRepresentation.withHostnameVerification();
            } else {
                internalRepresentation.withoutHostnameVerification();
            }
            return internalRepresentation;
        }
    }

    @ConfigGroup
    interface Pool {

        /**
         * {@return lag, if metrics are enabled}
         */
        @WithName("metrics.enabled")
        @WithDefault("false")
        boolean metricsEnabled();

        /**
         * {@return if leaked sessions logging is enabled}
         */
        @WithDefault("false")
        boolean logLeakedSessions();

        /**
         * {@return the maximum amount of connections in the connection pool towards a single database}
         */
        @WithDefault("100")
        int maxConnectionPoolSize();

        /**
         * Pooled connections that have been idle in the pool for longer than this timeout will be tested before they are used
         * again. The value {@literal 0} means connections will always be tested for validity and negative values mean
         * connections
         * will never be tested.
         *
         * @return the maximum idle time before connections are tested again
         */
        @WithDefault("-0.001S")
        Duration idleTimeBeforeConnectionTest();

        /**
         * Pooled connections older than this threshold will be closed and removed from the pool.
         *
         * @return the lifetime of a connection
         */
        @WithDefault("1H")
        Duration maxConnectionLifetime();

        /**
         * Acquisition of new connections will be attempted for at most configured timeout.
         *
         * @return the acquisition timeout
         */
        @WithDefault("1M")
        Duration connectionAcquisitionTimeout();
    }
}
