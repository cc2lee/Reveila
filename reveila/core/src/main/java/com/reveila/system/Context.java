package com.reveila.system;

import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

public interface Context {

    Logger getLogger();
    Optional<Proxy> getProxy(String name);
    Properties getProperties();
    PlatformAdapter getPlatformAdapter();
}