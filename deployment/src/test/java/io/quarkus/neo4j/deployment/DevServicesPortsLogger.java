package io.quarkus.neo4j.deployment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;

/**
 * Part of the application under test in {@link DevServicesDiscoveredContainerTest}: Dev mode tests cannot inject into
 * the test class, so the resolved ports are written to the log.
 */
@ApplicationScoped
public class DevServicesPortsLogger {

    private static final Logger LOG = Logger.getLogger(DevServicesPortsLogger.class);

    void onStart(@Observes StartupEvent event) {
        var config = ConfigProvider.getConfig();
        for (var name : new String[] { "bolt-port", "http-port" }) {
            var value = config.getOptionalValue("quarkus.neo4j.devservices." + name, String.class).orElse("<absent>");
            LOG.info("Neo4j Dev Services " + name + "=" + value);
        }
    }
}
