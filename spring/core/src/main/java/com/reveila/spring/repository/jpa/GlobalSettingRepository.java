package com.reveila.spring.repository.jpa;

import com.reveila.data.JavaObjectRepository;
import com.reveila.spring.model.jpa.GlobalSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for Global Settings.
 */
@Repository
public interface GlobalSettingRepository extends JpaRepository<GlobalSetting, String>, JavaObjectRepository<GlobalSetting, String> {
}
