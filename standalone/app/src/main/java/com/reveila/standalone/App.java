package com.reveila.standalone;

import reveila.Reveila;
import reveila.system.RuntimeUtil;

public class App {
    public static void main(String[] args) {
        Reveila reveila = new Reveila();
        try {
            reveila.start(new DefaultPlatformAdapter(RuntimeUtil.getJvmArgsAsProperties(args)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
