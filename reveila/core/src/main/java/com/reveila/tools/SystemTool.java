package com.reveila.tools;

import java.util.Collections;

public class SystemTool {
    public static String runPowerShell(String script) {
        ReveilaTerminal terminal = new ReveilaTerminal();
        return terminal.executeSafeScript(script, Collections.emptyList());
    }
}
