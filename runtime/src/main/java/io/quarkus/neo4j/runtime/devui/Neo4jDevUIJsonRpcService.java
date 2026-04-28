package io.quarkus.neo4j.runtime.devui;

import java.net.URI;

import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.common.annotation.NonBlocking;

public final class Neo4jDevUIJsonRpcService {

    @NonBlocking
    public String getBrowserUrl() {
        var config = ConfigProvider.getConfig();
        var neo4jUri = config.getOptionalValue("quarkus.neo4j.uri", String.class).orElse(null);
        var httpPort = config.getOptionalValue("quarkus.neo4j.devservices.http-port", String.class).orElse(null);
        if (neo4jUri != null && httpPort != null) {
            var parsed = URI.create(neo4jUri);
            return String.format("http://%s:%s/browser?dbms=bolt://neo4j@%s:%d",
                    parsed.getHost(), httpPort, parsed.getHost(), parsed.getPort());
        }
        return "";
    }
}
