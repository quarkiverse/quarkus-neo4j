package io.quarkus.it.neo4j;

// tag::skeleton[]
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.context.ThreadContext;
import org.neo4j.driver.Driver;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.async.ResultCursor;
import org.neo4j.driver.exceptions.NoSuchRecordException;

@Path("/fruits")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FruitResource {

    @Inject
    Driver driver;

    @Inject
    ThreadContext threadContext; // <.>
    // end::skeleton[]

    // tag::reading[]
    @GET
    public CompletionStage<Response> get() {
        AsyncSession session = driver.session(AsyncSession.class); // <.>
        CompletionStage<List<Fruit>> cs = session
                .executeReadAsync(tx -> tx
                        .runAsync("MATCH (f:Fruit) RETURN f ORDER BY f.name") // <.>
                        .thenCompose(cursor -> cursor // <.>
                                .listAsync(record -> Fruit.from(record.get("f").asNode()))));
        return threadContext.withContextCapture(cs) // <.>
                .thenCompose(fruits -> // <.>
                session.closeAsync().thenApply(signal -> fruits))
                .thenApply(Response::ok) // <.>
                .thenApply(ResponseBuilder::build);
    }
    // end::reading[]

    // tag::create[]
    @POST
    public CompletionStage<Response> create(Fruit fruit) {
        AsyncSession session = driver.session(AsyncSession.class);
        CompletionStage<Fruit> cs = session
                .executeWriteAsync(tx -> tx
                        .runAsync(
                                "CREATE (f:Fruit {id: randomUUID(), name: $name}) RETURN f",
                                Map.of("name", fruit.name))
                        .thenCompose(ResultCursor::singleAsync)
                        .thenApply(record -> Fruit.from(record.get("f").asNode())));
        return threadContext.withContextCapture(cs)
                .thenCompose(persistedFruit -> session
                        .closeAsync().thenApply(signal -> persistedFruit))
                .thenApply(persistedFruit -> Response
                        .created(URI.create("/fruits/" + persistedFruit.id))
                        .build());
    }
    // end::create[]

    // tag::getSingle[]
    @GET
    @Path("/{id}")
    public CompletionStage<Response> getSingle(String id) {
        AsyncSession session = driver.session(AsyncSession.class);
        return threadContext.withContextCapture(session
                .executeReadAsync(tx -> tx
                        .runAsync("MATCH (f:Fruit) WHERE f.id = $id RETURN f", Map.of("id", id))
                        .thenCompose(ResultCursor::singleAsync))
                .handle((record, exception) -> {
                    if (exception != null) {
                        Throwable source = exception;
                        if (exception instanceof CompletionException) {
                            source = exception.getCause();
                        }
                        Status status = Status.INTERNAL_SERVER_ERROR;
                        if (source instanceof NoSuchRecordException) {
                            status = Status.NOT_FOUND;
                        }
                        return Response.status(status).build();
                    } else {
                        return Response.ok(Fruit.from(record.get("f").asNode())).build();
                    }
                }))
                .thenCompose(response -> session.closeAsync().thenApply(signal -> response));
    }
    // end::getSingle[]

    // tag::delete[]
    @DELETE
    @Path("{id}")
    public CompletionStage<Response> delete(String id) {
        AsyncSession session = driver.session(AsyncSession.class);
        return threadContext.withContextCapture(session
                .executeWriteAsync(tx -> tx
                        .runAsync("MATCH (f:Fruit) WHERE f.id = $id DELETE f", Map.of("id", id))
                        .thenCompose(ResultCursor::consumeAsync) // <.>
                ))
                .thenCompose(response -> session.closeAsync())
                .thenApply(signal -> Response.noContent().build());
    }
    // end::delete[]

    // tag::skeleton[]
}
// end::skeleton[]
