package com.reveila.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReveilaTerminal {

    public String executeSafeScript(String scriptPath, List<String> args) {
        try {
            List<String> command = new ArrayList<>();
            // Detect OS to choose the right shell
            // Updated Java logic for Reveila-Suite
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Check if pwsh is available first (Modern PS 7)
                // then fall back to powershell (Legacy PS 5.1)
                String shell = isCommandAvailable("pwsh") ? "pwsh.exe" : "powershell.exe";
                command.addAll(List.of(shell, "-ExecutionPolicy", "Bypass", "-File", scriptPath));
            } else {
                command.addAll(List.of("bash", scriptPath));
            }
            command.addAll(args);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Merge error and output

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String output = reader.lines().collect(Collectors.joining("\n"));
                process.waitFor();
                return output;
            }
        } catch (Exception e) {
            return "Execution Error: " + e.getMessage();
        }
    }
}