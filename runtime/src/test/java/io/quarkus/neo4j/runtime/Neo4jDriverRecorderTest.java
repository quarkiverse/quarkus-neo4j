package io.quarkus.neo4j.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.driver.AuthTokens;

class Neo4jDriverRecorderTest {

    @Test // GH-168
    void authTokenShouldBeNoneWhenDisabled() {

        var configuration = new Neo4jConfiguration();
        configuration.authentication = new Neo4jConfiguration.Authentication();
        configuration.authentication.disabled = true;
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.none());
    }

    @Test // GH-168
    void shouldUseUserNamePassword() {

        var configuration = new Neo4jConfiguration();
        configuration.authentication = new Neo4jConfiguration.Authentication();
        configuration.authentication.value = Optional.empty();
        configuration.authentication.username = "foo";
        configuration.authentication.password = "bar";
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.basic("foo", "bar"));
    }

    @Test // GH-168
    void shouldUseValue() {

        var configuration = new Neo4jConfiguration();
        configuration.authentication = new Neo4jConfiguration.Authentication();
        configuration.authentication.value = Optional.of("foo/bar");
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.basic("foo", "bar"));
    }

    @Test // GH-168
    void valueShouldHavePrecedence() {

        var configuration = new Neo4jConfiguration();
        configuration.authentication = new Neo4jConfiguration.Authentication();
        configuration.authentication.value = Optional.of("foo/bar");
        configuration.authentication.username = "wurst";
        configuration.authentication.password = "salat";
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.basic("foo", "bar"));
    }

    @ParameterizedTest // GH-168
    @ValueSource(strings = { "foo", "/foo", "foo/" })
    void invalidAuthValuesShouldBeCaught(String value) {

        assertThatIllegalArgumentException().isThrownBy(() -> Neo4jDriverRecorder.toAuthToken(value))
                .withMessage(
                        "Invalid value for NEO4J_AUTH, the only supported format is <username>/<password>, neither username nor password are optional");
    }
}