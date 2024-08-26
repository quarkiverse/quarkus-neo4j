package io.quarkus.neo4j.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.driver.AuthTokens;

class Neo4jDriverRecorderTest {

    @Test // GH-168
    void authTokenShouldBeNoneWhenDisabled() {

        var configuration = mock(Neo4jConfiguration.class);
        var authentication = mock(Neo4jConfiguration.Authentication.class);
        when(authentication.disabled()).thenReturn(true);
        when(configuration.authentication()).thenReturn(authentication);
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.none());
    }

    @Test // GH-168
    void shouldUseUserNamePassword() {

        var configuration = mock(Neo4jConfiguration.class);
        var authentication = mock(Neo4jConfiguration.Authentication.class);
        when(authentication.value()).thenReturn(Optional.empty());
        when(authentication.username()).thenReturn("foo");
        when(authentication.password()).thenReturn("bar");
        when(configuration.authentication()).thenReturn(authentication);
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.basic("foo", "bar"));
    }

    @Test // GH-168
    void shouldUseValue() {

        var configuration = mock(Neo4jConfiguration.class);
        var authentication = mock(Neo4jConfiguration.Authentication.class);
        when(authentication.value()).thenReturn(Optional.of("foo/bar"));
        when(configuration.authentication()).thenReturn(authentication);
        var authToken = Neo4jDriverRecorder.getAuthToken(configuration);
        assertThat(authToken).isEqualTo(AuthTokens.basic("foo", "bar"));
    }

    @Test // GH-168
    void valueShouldHavePrecedence() {

        var configuration = mock(Neo4jConfiguration.class);
        var authentication = mock(Neo4jConfiguration.Authentication.class);
        when(authentication.value()).thenReturn(Optional.of("foo/bar"));
        when(authentication.username()).thenReturn("wurst");
        when(authentication.password()).thenReturn("salat");
        when(configuration.authentication()).thenReturn(authentication);
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
