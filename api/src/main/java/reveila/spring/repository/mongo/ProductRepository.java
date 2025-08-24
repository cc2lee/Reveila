package reveila.spring.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import reveila.spring.model.mongo.Product;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
}
