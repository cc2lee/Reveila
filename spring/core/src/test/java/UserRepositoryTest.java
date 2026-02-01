import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.reveila.data.EntityMapper;
import com.reveila.data.Filter;
import com.reveila.data.Page;
import com.reveila.spring.data.Organization;
import com.reveila.spring.data.User;
import com.reveila.spring.data.UserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({EntityMapper.class}) // Import any required config beans
class UserRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository(entityManager);
        
        // 1. Setup Test Data
        Organization org = new Organization();
        org.setName("Reveila Corp");
        entityManager.persist(org);

        User user = new User("charles_lee", "password123", Collections.emptyList());
        user.setEnabled(true);
        // Assuming you add an org setter to the User class
        // user.setOrg(org); 
        entityManager.persist(user);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find User by Organization Name using Dot Notation")
    void testSearchByNestedOrganizationName() {
        // 2. Create a Filter targeting "org.name"
        Filter filter = new Filter();
        filter.add("org.name", "Reveila", Filter.SearchOp.LIKE);

        // 3. Execute Search
        Page<User> result = userRepository.findAll(filter, null, List.of("org"), 0, 10, true);

        // 4. Verify Results
        assertFalse(result.getContent().isEmpty(), "User should be found");
        assertEquals("charles_lee", result.getContent().get(0).getUsername());
        assertEquals("Reveila Corp", result.getContent().get(0).getOrg().getName());
    }
}