package reveila.adapters.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;

import com.mongodb.MongoException;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
//import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
//import static com.mongodb.client.model.Filters.eq;

public class MongoDB implements AutoCloseable {

    public static void main(String[] args) {
        try (MongoDB mongoDB = new MongoDB(
            "mongodb+srv://cc2lee:4DNC9GMenknPbhnh@cluster0.emzc59r.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0",
            "admin"
        )) {
            mongoDB.connect();
            mongoDB.test();
        }
    }

    private String databaseName; // = "admin";
    private String connectionString; // = "mongodb+srv://cc2lee:4DNC9GMenknPbhnh@cluster0.emzc59r.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
    private transient MongoClient mongoClient;
    private transient MongoDatabase database;

    public MongoDB(String connectionString, String databaseName) {
        this.connectionString = connectionString;
        this.databaseName = databaseName;
    }

    public MongoDatabase getDatabase() {
        if (database == null) {
            database = getMongoClient().getDatabase(databaseName);
        }
        return database;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public MongoClient getMongoClient() {
        if (mongoClient == null) {
            mongoClient = MongoClients.create(connectionString);
        }
        return mongoClient;
    }

    public void connect() {
        ServerApi serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .serverApi(serverApi)
                .build();

        // Create a new client and connect to the server
        mongoClient = MongoClients.create(settings);
        database = mongoClient.getDatabase(databaseName);
    }

    public void test() {
        try {
            database.runCommand(new Document("ping", 1));
            System.out.println("Pinged your deployment. You successfully connected to MongoDB!");

            /* Uncomment the following lines to test document retrieval
            MongoCollection<Document> collection = database.getCollection("movies");
            Document doc = collection.find(eq("title", "Back to the Future")).first();
            if (doc != null) {
                System.out.println(doc.toJson());
            } else {
                System.out.println("No matching documents found.");
            }
            */
        } catch (MongoException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
