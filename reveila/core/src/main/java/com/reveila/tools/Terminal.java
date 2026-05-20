package com.reveila.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.reveila.error.SystemException;

/**
 * Terminal execution utility for the Reveila-Suite.
 * Handles both predefined script execution and dynamic on-the-fly execution.
 * 
 * @author Charles Lee
 */
public class Terminal {

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final Logger LOGGER = Logger.getLogger(Terminal.class.getName());

    private Path secureScriptDirectory;

    public Path getSecureScriptDirectory() {
        return secureScriptDirectory;
    }

    public static Terminal getInstance(String secureScriptDirectory) {
        return new Terminal(Paths.get(secureScriptDirectory));
    }

    private Terminal(Path secureScriptDirectory) {
        this.secureScriptDirectory = secureScriptDirectory;
    }

    /**
     * Executes a safe, predefined script from the filesystem.
     * 
     * @param scriptPath The absolute path to the script file.
     * @param args       Arguments to pass to the script.
     * @return The combined stdout and stderr output of the script, or an error
     *         message if it fails.
     * @throws SystemException
     * @throws IOException
     */
    public String executeSafeScript(String scriptPath, List<String> args) throws SystemException {
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Script execution was interrupted: " + e.getMessage();
        } catch (IOException e) {
            throw new SystemException("Failed to execute script: " + e.getMessage(), e);
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
        Path tempScriptPath = null;
        boolean isWindows = isWindows();
        String extension = isWindows ? ".ps1" : ".sh";
        try {
            tempScriptPath = Files.createTempFile(secureScriptDirectory, "reveila_dynamic_script_", extension);
            Files.writeString(tempScriptPath, rawScriptContent);
        } catch (IOException e) {
            return "Failed to create temporary script file: " + e.getMessage();
        }

        List<String> command = new ArrayList<>();
        if (isWindows) {
            String shell;
            try {
                shell = isCommandAvailable("pwsh") ? "pwsh.exe" : "powershell.exe";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Script execution was interrupted: " + e.getMessage();
            } catch (IOException e) {
                return "Error checking shell availability: " + e.getMessage();

            }
            command.addAll(List.of(shell, "-ExecutionPolicy", "Bypass", "-File", tempScriptPath.toString()));
        } else {
            command.addAll(List.of("bash", tempScriptPath.toString()));
        }

        try {
            return runProcess(command);
        } catch (SystemException e) {
            return "Execution Error: " + e.getMessage();
        } finally {
            try {
                Files.deleteIfExists(tempScriptPath);
            } catch (Exception e) {
                LOGGER.warning(
                        "Failed to delete temporary script file: " + tempScriptPath + " cause: " + e.getMessage());
            }
        }
    }

    /**
     * Internal helper to run a process with a 30-second timeout.
     * 
     * @param command The full command and arguments to execute.
     * @return Captured output.
     * @throws InterruptedException
     * @throws IOException
     * @throws Exception            if process execution fails or times out.
     */
    private String runProcess(List<String> command) throws SystemException {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (Exception e) {
            throw new SystemException("Script execution failed: " + e.getMessage(), e);
        }

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
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new SystemException("Script execution was interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            process.destroyForcibly();
            throw new SystemException("Script execution failed: " + e.getMessage(), e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private boolean isCommandAvailable(String command) throws IOException, InterruptedException {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    isWindows() ? List.of("where", command) : List.of("which", command))
                    .start();
            return process.waitFor() == 0;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
    }
}
