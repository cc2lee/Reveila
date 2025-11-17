package com.reveila.standalone;

import reveila.Reveila;
import reveila.system.RuntimeUtil;

public class App {
    public static void main(String[] args) {
        System.out.println("Starting Reveila...");
        // A URL pointing to the configuration properties file can be passed in as an argument to the Java main class, using the following format:
        // java MyApp "reveila.properties=file:///C:/IDE/Projects/Reveila-Suite/reveila/runtime-directory/configs/reveila.properties"
        Reveila reveila = new Reveila();
        try {
            reveila.start(new DefaultPlatformAdapter(RuntimeUtil.getArgsAsProperties(args)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
