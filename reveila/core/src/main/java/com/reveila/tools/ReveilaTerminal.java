package com.reveila.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Terminal execution utility for the Reveila-Suite.
 * Handles both predefined script execution and dynamic on-the-fly execution.
 * 
 * @author Charles Lee
 */
public class ReveilaTerminal {

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Executes a safe, predefined script from the filesystem.
     * 
     * @param scriptPath The absolute path to the script file.
     * @param args       Arguments to pass to the script.
     * @return The combined stdout and stderr output of the script, or an error message if it fails.
     */
    public String executeSafeScript(String scriptPath, List<String> args) {
        try {
            List<String> command = new ArrayList<>();
            boolean isWindows = isWindows();

            if (isWindows) {
                String shell = isCommandAvailable("pwsh") ? "pwsh.exe" : "powershell.exe";
                command.addAll(List.of(shell, "-ExecutionPolicy", "Bypass", "-File", scriptPath));
            } else {
                command.addAll(List.of("bash", scriptPath));
            }
            command.addAll(args);

            return runProcess(command);
        } catch (Throwable e) {
            return "Execution Error: " + e.getMessage();
        }
    }

    /**
     * Executes raw script content by saving it to a temporary file and running it.
     * Adheres to ADR 0014 security policies including timeouts.
     * 
     * @param rawScriptContent The literal script source code to execute.
     * @return The output of the script execution or an error message.
     */
    public String executeDynamicScript(String rawScriptContent) {
        Path tempScript = null;
        try {
            boolean isWindows = isWindows();
            String extension = isWindows ? ".ps1" : ".sh";
            tempScript = Files.createTempFile("reveila_dynamic_", extension);
            Files.writeString(tempScript, rawScriptContent);

            List<String> command = new ArrayList<>();
            if (isWindows) {
                String shell = isCommandAvailable("pwsh") ? "pwsh.exe" : "powershell.exe";
                command.addAll(List.of(shell, "-ExecutionPolicy", "Bypass", "-File", tempScript.toString()));
            } else {
                command.addAll(List.of("bash", tempScript.toString()));
            }

            return runProcess(command);
        } catch (Throwable t) {
            return "Security/IO Error: " + t.getMessage();
        } finally {
            if (tempScript != null) {
                try {
                    Files.deleteIfExists(tempScript);
                } catch (Throwable e) {
                    // Log cleanup failure but do not overwrite results
                    System.err.println("Failed to clean up temporary script: " + tempScript);
                }
            }
        }
    }

    /**
     * Internal helper to run a process with a 30-second timeout.
     * 
     * @param command The full command and arguments to execute.
     * @return Captured output.
     * @throws Exception if process execution fails or times out.
     */
    private String runProcess(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return output.append("\n[ERROR] Script execution timed out after ")
                        .append(DEFAULT_TIMEOUT_SECONDS).append(" seconds.")
                        .toString();
            }

            return output.toString().trim();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(
                    isWindows() ? List.of("where", command) : List.of("which", command))
                    .start();
            return process.waitFor() == 0;
        } catch (Throwable e) {
            return false;
        }
    }
}
