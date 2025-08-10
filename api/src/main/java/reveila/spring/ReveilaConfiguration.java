package reveila.spring;

import java.util.Properties;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reveila.Reveila;
import reveila.error.ConfigurationException;


@Configuration
public class ReveilaConfiguration {

    /**
     * Creates a singleton instance of the Reveila engine.
     */
    @Bean
    public Reveila reveila() {
        return new Reveila();
    }

    /**
     * Starts the Reveila engine after the Spring application context is loaded.
     */
    @Bean
    public ApplicationRunner reveilaRunner(Reveila reveila, ApplicationArguments args) {
        return runnerArgs -> reveila.start(new reveila.platform.WindowsPlatformAdapter(splitArgs(args.getSourceArgs())));
    }

    private Properties splitArgs(String[] args) throws ConfigurationException {
		Properties cmdArgs = new Properties();
		if (args != null) {
			for (String arg : args) {
				String[] parts = arg.split("=", 2);
				if (parts.length == 2 && !parts[0].isEmpty()) {
					cmdArgs.put(parts[0], parts[1]);
				} else {
					// It's good practice to warn about arguments that don't fit the expected format.
					// Since the logger isn't configured yet, System.err is the best option.
					System.err.println("Warning: Ignoring malformed command-line argument: " + arg);
				}
			}
		}
		return cmdArgs;
	}
}