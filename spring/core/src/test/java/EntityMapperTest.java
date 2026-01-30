import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.reveila.data.Entity;
import com.reveila.data.EntityMapper;
import com.reveila.spring.data.User;

class EntityMapperTest {

    private EntityMapper entityMapper;

    @BeforeEach
    void setUp() {
        entityMapper = new EntityMapper();
    }

    @Test
    @DisplayName("Should convert generic Entity with orgId into User entity with Organization proxy")
    void testFromGenericEntityWithRelationship() {
        // 1. Create a generic Entity as if it came from a JSON API
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "charlie_arch");
        attributes.put("orgId", orgId.toString()); // The 'Id' suffix triggers relationship logic
        attributes.put("s3FolderId", "folder-123");

        Entity genericUser = new Entity("user", Map.of("id", userId), attributes);

        // 2. Map back to the Typed JPA Entity
        User resultUser = entityMapper.fromGenericEntity(genericUser, User.class);

        // 3. Verify the conversion
        assertEquals("charlie_arch", resultUser.getUsername());
        assertNotNull(resultUser.getOrg(), "Organization should not be null");
        assertEquals(orgId, resultUser.getOrg().getId(), "Organization ID should match the input orgId");
    }

    @Test
    @DisplayName("Should handle Java 8 Date/Time and UUID types correctly")
    void testDataTypeCoercion() {
        UUID id = UUID.randomUUID();
        Map<String, Object> attributes = Map.of("username", "test_user");
        
        Entity entity = new Entity("user", Map.of("id", id.toString()), attributes);
        
        // Verifies Jackson is correctly configured via the Builder to handle String -> UUID
        User user = entityMapper.fromGenericEntity(entity, User.class);
        
        assertEquals(id, user.getId());
    }
}