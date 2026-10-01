package com.reveila.system;

import java.util.Properties;
import java.util.logging.Logger;

public interface Context {

    Logger getLogger();
    Proxy getProxy(String name) throws com.reveila.error.SecurityException, IllegalArgumentException;
    Properties getProperties();
    PlatformAdapter getPlatformAdapter();
}