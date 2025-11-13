package reveila.service;

import java.util.Optional;

import reveila.error.ConfigurationException;
import reveila.system.AbstractService;
import reveila.system.Proxy;
import reveila.util.SortedPointsTracker;

public class RatedService extends AbstractService {

    private SortedPointsTracker pointsTracker;
    
    public RatedService(String name) {
        super();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Argument 'name' must not be null or empty");
        }
        
        this.pointsTracker = new SortedPointsTracker(name);
    }

    public void setInitialPoints(String providerNameAndPoints) throws ConfigurationException {
        try {
            String[] array = providerNameAndPoints.split(",");
            String providerName = array[0].trim();
            String points = array[1].trim();
            pointsTracker.applyPoints(Long.valueOf(points), providerName);
        } catch (Exception e) {
            throw new ConfigurationException("Invalid service provider points initialization format. Expected format: <provider-name> <initial-points>" + "\n" 
                + "Error details: " + e.toString(), e);
        }
    }

    public synchronized Object invoke(final String methodName, final Object[] args) throws Exception {
		String providerName = getBestProvider();
		Optional<Proxy> localProxy = systemContext.getProxy(providerName);
		if (!localProxy.isPresent()) {
			throw new ConfigurationException("Component '" + providerName + "' not found.");
		}
        Proxy proxy = localProxy.get();
		Object result = proxy.invoke(methodName, args);
		return result;
	}

    public void rateProvider(String provider, Long points) {
        if (provider == null || points == null) {
            return; // Ignore
        }
        pointsTracker.applyPoints(points, provider);
    }

    /*
     * This method depends on SortedPointsTracker to return the provider with the highest points.
     * The more the method rateProvider is called,
     * the more accurately it reflects the best provider.
     * 
     * Returns the provider name with the highest points.
     */
    public String getBestProvider() {
        return pointsTracker.getBest();
    }

    /*
     * This method depends on SortedPointsTracker to return the provider with the lowest points.
     * The more the method rateProvider is called,
     * the more accurately it reflects the worst provider.
     * 
     * Returns the provider name with the lowest points.
     */
    public String getWorstProvider() {
        return pointsTracker.getWorst();
    }
}
