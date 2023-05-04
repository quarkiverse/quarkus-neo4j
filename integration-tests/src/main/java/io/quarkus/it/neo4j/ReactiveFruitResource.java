// tag::get[]
package io.quarkus.it.neo4j;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("reactivefruits")
@Consumes(MediaType.APPLICATION_JSON)
public class ReactiveFruitResource {

    @Inject
    Driver driver;

    static Uni<Void> sessionFinalizer(ReactiveSession session) { // <.>
        return Uni.createFrom().publisher(session.close());
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> get() {
        // Create a stream from a resource we can close in a finalizer...
        return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class), // <.>
                session -> session.executeRead(tx -> {
                    var result = tx.run("MATCH (f:Fruit) RETURN f.name as name ORDER BY f.name");
                    return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
                }))
                .withFinalizer(ReactiveFruitResource::sessionFinalizer) // <.>
                .map(record -> record.get("name").asString());
    }
    // end::get[]

    // tag::create[]
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @ResponseStatus(201)
    public Uni<String> create(Fruit fruit) {

        return Uni.createFrom().emitter(e -> Multi.createFrom().resource(() -> driver.session(ReactiveSession.class), // <.>
                session -> session.executeWrite(tx -> {
                    var result = tx.run(
                            "CREATE (f:Fruit {id: randomUUID(), name: $name}) RETURN f",
                            Map.of("name", fruit.name));
                    return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
                }))
                .withFinalizer(ReactiveFruitResource::sessionFinalizer)
                .map(record -> Fruit.from(record.get("f").asNode()))
                .toUni()
                .subscribe().with( // <.>
                        persistedFruit -> e.complete("/fruits/" + persistedFruit.id)));
    }
    // end::create[]

    // tag::get[]
}
// end::get[]
