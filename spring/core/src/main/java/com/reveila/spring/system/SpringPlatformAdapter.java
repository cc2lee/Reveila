package com.reveila.spring.system;

import java.util.Properties;

import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reveila.data.Repository;
import com.reveila.platform.BasePlatformAdapter;
import com.reveila.spring.service.SpringEntityRepository;

/**
 * A custom PlatformAdapter that acts as a bridge to the Spring ApplicationContext.
 * This allows Reveila components to access Spring-managed beans.
 */
public class SpringPlatformAdapter extends BasePlatformAdapter {

    private ApplicationContext springContext;
    private Repository<?, ?> repository;
    private ObjectMapper objectMapper;

    public SpringPlatformAdapter(ApplicationContext context, Properties commandLineArgs) throws Exception {
        super(commandLineArgs);
        this.springContext = context;
        this.repository = getBean(SpringEntityRepository.class);
        this.objectMapper = getBean(ObjectMapper.class);
    }

    public <T> T getBean(Class<T> beanClass) {
        if (beanClass == null) {
            throw new IllegalArgumentException("Argument 'beanClass' must not be null.");
        }
        return springContext.getBean(beanClass);
    }

    @Override
    public Repository<?, ?> getRepository(String entityType) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRepository'");
    }
}