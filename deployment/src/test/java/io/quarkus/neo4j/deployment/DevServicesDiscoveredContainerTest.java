package io.quarkus.neo4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.LogRecord;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import io.quarkus.test.QuarkusDevModeTest;

/**
 * A shared Neo4j container that is already running (for example started by another application in dev mode) must be
 * reused with both its bolt and its http port.
 */
@Testcontainers(disabledWithoutDocker = true)
class DevServicesDiscoveredContainerTest {

    // Started by the Testcontainers extension, which runs before the dev mode extension below.
    @Container
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5")
            .withAdminPassword("verysecret")
            .withLabel("quarkus-dev-service-neo4j", "neo4j");

    @RegisterExtension
    static final QuarkusDevModeTest TEST = new QuarkusDevModeTest()
            .withApplicationRoot(jar -> jar
                    .addClass(DevServicesPortsLogger.class)
                    .addAsResource(new StringAsset("quarkus.neo4j.devservices.shared=true\n"), "application.properties"))
            .setLogRecordPredicate(record -> record.getMessage() != null
                    && record.getMessage().startsWith("Neo4j Dev Services "));

    @Test
    void shouldExposePortsOfDiscoveredContainer() {

        assertThat(TEST.getLogRecords())
                .extracting(LogRecord::getMessage)
                .contains(
                        "Neo4j Dev Services bolt-port=" + NEO4J.getMappedPort(Neo4jDevServicesProcessor.DEFAULT_BOLT_PORT),
                        "Neo4j Dev Services http-port=" + NEO4J.getMappedPort(Neo4jDevServicesProcessor.DEFAULT_HTTP_PORT));
    }
}
