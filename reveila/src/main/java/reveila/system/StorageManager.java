package reveila.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * @author Charles Lee
*/
public final class StorageManager {

	private PlatformAdapter platformAdapter;
	private Proxy proxy;

	public StorageManager(PlatformAdapter platformAdapter, Proxy proxy) {
		super();
		this.proxy = Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");
		this.platformAdapter = Objects.requireNonNull(platformAdapter, "Argument 'PlatformAdapter' must not be null");
	}

	public InputStream getInputStream(int storageType, String relativePath) throws IOException {
		return this.platformAdapter.getInputStream(storageType, proxy.getName() + File.separator + removeLeadingFileSeparator(relativePath));
	}

	public OutputStream getOutputStream(int storageType, String relativePath) throws IOException {
		return this.platformAdapter.getOutputStream(storageType, proxy.getName() + File.separator + removeLeadingFileSeparator(relativePath));
	}

	private String removeLeadingFileSeparator(String path) {
		if (path == null || path.isEmpty() || !path.startsWith(File.separator)) { return path; }
		return path.substring(path.indexOf(File.separator) + 1);
	}

}