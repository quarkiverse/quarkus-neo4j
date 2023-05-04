package io.quarkus.it.neo4j;

import static jakarta.ws.rs.core.MediaType.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.context.ThreadContext;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("/neo4j")
public class Neo4jResource {

    @Inject
    Driver driver;

    @Inject
    ThreadContext threadContext;

    @GET
    @Path("/blocking")
    public String doStuffWithNeo4j() {
        try {
            createNodes(driver);

            readNodes(driver);
        } catch (Exception e) {
            StringWriter out = new StringWriter();
            PrintWriter writer = new PrintWriter(out);
            reportException("An error occurred while performing Neo4j operations", e, writer);
            writer.flush();
            writer.close();
            return out.toString();
        }
        return "OK";
    }

    @GET
    @Path("/asynchronous")
    @Produces(APPLICATION_JSON)
    public CompletionStage<List<Integer>> doStuffWithNeo4jAsynchronous() {
        AsyncSession session = driver.session(AsyncSession.class);
        return threadContext.withContextCapture(session
                .runAsync("UNWIND range(1, 3) AS x RETURN x"))
                .thenCompose(cursor -> cursor.listAsync(record -> record.get("x").asInt()))
                .whenComplete((records, error) -> {
                    if (records != null) {
                        System.out.println(records);
                    } else {
                        error.printStackTrace();
                    }
                })
                .thenCompose(records -> session.closeAsync()
                        .thenApply(ignore -> records));
    }

    @GET
    @Path("/reactive")
    @Produces(SERVER_SENT_EVENTS)
    public Multi<Integer> doStuffWithNeo4jReactive() {

        return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
                session -> session.executeRead(tx -> {
                    var result = tx.run("UNWIND range(1, 3) AS x RETURN x");
                    return Multi.createFrom().publisher(result)
                            .flatMap(ReactiveResult::records)
                            .map(record -> record.get("x").asInt());
                }))
                .withFinalizer(session -> {
                    return Uni.createFrom().publisher(session.close());
                });
    }

    private static void createNodes(Driver driver) {
        try (Session session = driver.session();
                Transaction transaction = session.beginTransaction()) {
            transaction.run("CREATE (f:Framework {name: $name}) - [:CAN_USE] -> (n:Database {name: 'Neo4j'})",
                    Values.parameters("name", "Quarkus"));
            transaction.commit();
        }
    }

    private static void readNodes(Driver driver) {
        try (Session session = driver.session();
                Transaction transaction = session.beginTransaction()) {
            Result result = transaction
                    .run("MATCH (f:Framework {name: $name}) - [:CAN_USE] -> (n) RETURN f, n",
                            Values.parameters("name", "Quarkus"));
            result.forEachRemaining(
                    record -> System.out.printf("%s works with %s%n", record.get("n").get("name").asString(),
                            record.get("f").get("name").asString()));
            transaction.commit();
        }
    }

    private void reportException(String errorMessage, final Exception e, final PrintWriter writer) {
        if (errorMessage != null) {
            writer.write(errorMessage);
            writer.write(" ");
        }
        writer.write(e.toString());
        writer.append("\n\t");
        e.printStackTrace(writer);
        writer.append("\n\t");
    }
}
