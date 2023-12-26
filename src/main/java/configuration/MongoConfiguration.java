package configuration;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class MongoConfiguration {

    private final static String AGGREGATE_ID = "aggregateId";

    private final static Logger logger = Logger.getLogger(MongoConfiguration.class);

    @Inject
    ReactiveMongoClient mongoClient;

    @ConfigProperty(name = "mongodb.database", defaultValue = "microservices")
    String database;

    @ConfigProperty(name = "mongodb.bank-account-collection", defaultValue = "bankAccounts")
    String bankAccountsCollection;

    void startup(@Observes StartupEvent event) {//18.12.2023 mine-->this is usually a good place to initialize the db or the cache
        //or any other startup work
        mongoClient.getDatabase(database)
                .listCollectionNames().toUni() //17.11.2023 mine--> Convert to unit to be able to use the fluent API that follows
                .onFailure().invoke(Throwable::printStackTrace)
                .chain(collections -> { //18.12.2023 mine-->When you see chain it means onItem().transformToUni() and a Uni<T>
                    //will be returned
                    logger.infof("startup mongo collections: %s", collections);
                    if (collections != null && collections.contains(bankAccountsCollection)) {
                        return Uni.createFrom().voidItem();
                    }
                    final var indexOptions = new IndexOptions().unique(true);
                    return mongoClient.getDatabase(database)
                            .createCollection(bankAccountsCollection) 
                            .onFailure().invoke(Throwable::printStackTrace) //18.12.2023 mine-->We do call when the function called/
                            //is async, invoke is only used for sync operations. The item returned is never modified when you
                            //invoke() or call(), prefer to use class method reference.
                            .chain(v -> mongoClient.getDatabase(database)
                                    .getCollection(bankAccountsCollection)
                                    .createIndex(Indexes.ascending(AGGREGATE_ID), indexOptions));
                })//18.12.2023 mine-->we call subscribe at the end to get the results.
                .subscribe().with(result -> logger.infof("listCollections: %s", result));
                //18.12.2023 mine-->We usually also do failure, and or complete.


        mongoClient.getDatabase(database).getCollection(bankAccountsCollection)
                .listIndexes().collect().asList()
                .onFailure().invoke(Throwable::printStackTrace)
                .subscribe().with(result -> logger.infof("bankAccounts indexes: %s", result));
    }
}
