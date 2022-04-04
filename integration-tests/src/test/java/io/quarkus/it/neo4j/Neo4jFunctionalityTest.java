package io.quarkus.it.neo4j;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Response.Status;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * Test connecting via Neo4j Java-Driver to Neo4j.
 * Can quickly start a matching database with:
 *
 * <pre>
 *     docker run --publish=7474:7474 --publish=7687:7687 -e 'NEO4J_AUTH=neo4j/secret' neo4j:4.4
 * </pre>
 */
@QuarkusTest
public class Neo4jFunctionalityTest {

    static Driver driver;

    private Long apfelId;

    @BeforeAll
    public static void connectDriver() throws IOException {

        var properties = new Properties();
        properties.load(Neo4jFunctionalityTest.class.getResourceAsStream("/application.properties"));
        var defaultUri = "bolt://localhost:7687";
        var uri = properties.getProperty("quarkus.neo4j.uri", defaultUri);
        if (uri.startsWith("$")) {
            uri = defaultUri;
        }
        var defaultPassword = "secret";
        var password = properties.getProperty("quarkus.neo4j.authentication.password", defaultPassword);
        if (uri.startsWith("$")) {
            password = defaultPassword;
        }
        driver = GraphDatabase.driver(uri, AuthTokens.basic("neo4j", password));
    }

    @AfterAll
    public static void closeDriver() {

        driver.close();
    }

    @BeforeEach
    public void prepareFruits() {

        try (var session = driver.session()) {

            session.run("MATCH (f:Fruit) DETACH DELETE f").consume();
            apfelId = session.run("MERGE (f:Fruit {name: 'Apfel'}) RETURN id(f)")
                    .single().get(0).asLong();
        }
    }

    @Test
    void getFruitsShouldWork() {

        var response = RestAssured.given()
                .when().get("/fruits/")
                .then().statusCode(Status.OK.getStatusCode())
                .extract().jsonPath();

        assertEquals(apfelId, response.getLong("[0].id"));
        assertEquals("Apfel", response.getString("[0].name"));
    }

    @Test
    void getReactiveFruitsShouldWork() {

        RestAssured.given()
                .when().get("/reactivefruits/")
                .then().statusCode(Status.OK.getStatusCode())
                .statusCode(200)
                .body(containsString("data:Apfel"));
    }

    @Test
    void createFruitsShouldWork() {

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(new Fruit("Kartoffel"))
                .when().post("/fruits/")
                .then().statusCode(Status.CREATED.getStatusCode())
                .header("Location", matchesRegex("/fruits/\\d+"));
    }

    @Test
    void createReactiveFruitsShouldWork() {

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(new Fruit("Kartoffel"))
                .when().post("/reactivefruits/")
                .prettyPeek()
                .then().statusCode(Status.CREATED.getStatusCode())
                .body(matchesRegex("/fruits/\\d+"));
    }

    @Test
    void getSingleFruitShouldWork() {

        var response = RestAssured.given()
                .when().get("/fruits/" + apfelId)
                .then().statusCode(Status.OK.getStatusCode())
                .extract().jsonPath();

        assertEquals(apfelId, response.getLong("id"));
        assertEquals("Apfel", response.getString("name"));
    }

    @Test
    void deleteFruitShouldWork() {

        var response = RestAssured.given()
                .when().delete("/fruits/" + apfelId)
                .prettyPeek()
                .then().statusCode(Status.NO_CONTENT.getStatusCode())
                .extract().jsonPath();
    }

    @Test
    public void testBlockingNeo4jFunctionality() {
        RestAssured.given().when().get("/neo4j/blocking").then().body(is("OK"));
    }

    @Test
    public void testAsynchronousNeo4jFunctionality() {
        RestAssured.given()
                .when().get("/neo4j/asynchronous")
                .then().statusCode(200)
                .body(is(equalTo(Stream.of(1, 2, 3).map(Object::toString).collect(Collectors.joining(",", "[", "]")))));
    }

    @Test
    public void testReactiveNeo4jFunctionality() {
        RestAssured.given()
                .when().get("/neo4j/reactive")
                .prettyPeek()
                .then().statusCode(200)
                .contentType("text/event-stream");
    }

    @Test
    public void health() {
        RestAssured.when().get("/q/health/ready").then()
                .log().all()
                .body("status", is("UP"),
                        "checks.status", containsInAnyOrder("UP"),
                        "checks.name", containsInAnyOrder("Neo4j connection health check"),
                        "checks.data.server", containsInAnyOrder(matchesRegex("Neo4j/.*@.*:\\d*")),
                        "checks.data.edition", containsInAnyOrder(is(notNullValue())));
    }

    @Test
    public void metrics() {
        RestAssured.given().when().get("/neo4j/blocking").then().body(is("OK"));
        assertMetricValue("neo4j.acquired", greaterThan(0));
        assertMetricValue("neo4j.created", greaterThan(0));
        assertMetricValue("neo4j.totalAcquisitionTime", greaterThan(0));
        assertMetricValue("neo4j.totalConnectionTime", greaterThan(0));
        assertMetricValue("neo4j.totalInUseTime", greaterThan(0));
    }

    private void assertMetricValue(String name, Matcher<Integer> valueMatcher) {
        RestAssured
                .given().accept(ContentType.JSON)
                .when().get("/q/metrics/vendor/" + name)
                .then()
                .body("'" + name + "'", valueMatcher);
    }
}
