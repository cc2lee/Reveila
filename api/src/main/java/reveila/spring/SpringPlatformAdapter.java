package reveila.spring;

import java.util.Properties;

import org.springframework.context.ApplicationContext;

import reveila.platform.DefaultPlatformAdapter;

/**
 * A custom PlatformAdapter that acts as a bridge to the Spring ApplicationContext.
 * This allows Reveila components to access Spring-managed beans.
 */
public class SpringPlatformAdapter extends DefaultPlatformAdapter {

    private final ApplicationContext applicationContext;

    public SpringPlatformAdapter(ApplicationContext applicationContext, Properties commandLineArgs) throws Exception {
        super(commandLineArgs);
        this.applicationContext = applicationContext;
    }

    public <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass);
    }
}