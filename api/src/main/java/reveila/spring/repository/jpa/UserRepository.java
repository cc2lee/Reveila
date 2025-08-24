package reveila.spring.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import reveila.spring.model.jpa.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
