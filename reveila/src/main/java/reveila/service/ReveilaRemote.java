package reveila.service;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import reveila.error.ConfigurationException;
import reveila.error.SystemException;
import reveila.system.AbstractService;
import reveila.system.JsonException;
import reveila.system.NodePerformanceTracker;
import reveila.util.JsonUtil;

/**
 * A system component that can invoke methods on a remote Reveila instance via its REST API.
 * This acts as a proxy, allowing any Reveila client (mobile or backend) to interact
 * with another Reveila instance, enabling clustered or distributed setups.
 */
public class ReveilaRemote extends AbstractService {
    
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // OkHttpClient is thread-safe and designed to be shared.
    // Making it static ensures a single instance is reused across all invocations,
    // which is more efficient as it allows for connection pooling.
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private Map<URL, Number> configs = Collections.synchronizedMap(new HashMap<URL, Number>());
    private NodePerformanceTracker nodePerformanceTracker = NodePerformanceTracker.getInstance();
    
    /**
     * No-arg constructor for instantiation by the Reveila engine.
     */
    public ReveilaRemote() {
        super();
    }

    /**
     * Sets the endpoint URL for a remote Reveila instance.
     * This method is called by the Reveila engine during component initialization
     * and can be called repeatedly based on the 'arguments' in the component's configuration.
     * Under the hood, this method adds the URL to a pool of URLs tracked as a cluster.
     * The Reveila invocation logic selects the most efficient node from this cluster to route requests.
     *
     * @param url The base URL of the Reveila instance followed by a space and a priority integer.
     */
    public void setRemoteURLs(String[] baseUrls) throws ConfigurationException {
        for (String url : baseUrls) {
            try {
                addRemoteNode(url);
            } catch (SystemException e) {
                throw new ConfigurationException(
                    "Error setting remote instance base URL: '" + url + "'. Check format: <URL>, <priority>" + "\n" 
                    + "Example: http://127.0.0.1:8080, 1" + "\n"
                    + "Error details: " + e.toString(), e);
            }
        }
    }

    public void addRemoteNode(String urlAndPriority) throws SystemException {
        try {
            String[] array = urlAndPriority.split(",");
            URL url = new URL(array[0].trim());
            Long priority = Long.parseLong(array[1].trim());
            configs.put(url, priority);
        } catch (Exception e) {
            throw new SystemException(
                "Error setting remote instance base URL. Check format: <URL>, <priority>" + "\n" 
                + "Example: http://127.0.0.1:8080, 1" + "\n"
                + "Error details: " + e.toString(), e);
        }
    }

    @Override
    public synchronized void start() throws Exception {
        super.start();
        Set<Entry<URL, Number>> entrySet = configs.entrySet();
        for (Entry<URL, Number> entry : entrySet) {
            nodePerformanceTracker.track(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        super.stop();
        nodePerformanceTracker.clear();
    }

    public Object invoke(String componentName, String methodName) throws IOException, JsonException {
        return invoke(new Object[]{componentName, methodName});
    }

    public Object invoke(String componentName, String methodName, Object arg1) throws IOException, JsonException {
        return invoke(new Object[]{componentName, methodName, arg1});
    }

    public Object invoke(String componentName, String methodName, Object arg1, Object arg2) throws IOException, JsonException {
        return invoke(new Object[]{componentName, methodName, arg1, arg2});
    }

    public Object invoke(String componentName, String methodName, Object arg1, Object arg2, Object arg3) throws IOException, JsonException {
        return invoke(new Object[]{componentName, methodName, arg1, arg2, arg3});
    }

    /*
     * Invokes a method on a remote Reveila component.
     * The first argument can optionally be a URL object specifying the target Reveila instance.
     * If no URL is provided, the method uses the best available node from the Cluster.
     * The next two arguments must be the component name and method name, followed by any method
     * parameters as an array or individual arguments.
     */
    public Object invoke(Object... remoteCallArgs) throws IOException, JsonException {
        if (remoteCallArgs == null || remoteCallArgs.length < 2) {
            throw new IllegalArgumentException(
                "The remote 'invoke' requires at least 2 arguments: componentName, and methodName");
        }

        long startTime = System.currentTimeMillis();
        int argOffset = 2;
        String componentName;
        String methodName;
        URL baseUrl;

        if (remoteCallArgs[0] instanceof URL) {
            baseUrl = (URL) remoteCallArgs[0];
            argOffset = 3;
            componentName = (String) remoteCallArgs[1];
            methodName = (String) remoteCallArgs[2];
        } else {
            baseUrl = nodePerformanceTracker.getBestNodeUrl();
            if (baseUrl == null) {
                throw new IllegalStateException("No 'BaseURL' argument specified in the component configuration. "
                + "To use ReveilaRemote, you must specify at least one valid end-point URL.");
            }
            componentName = (String) remoteCallArgs[0];
            methodName = (String) remoteCallArgs[1];
        }
        
        Object[] args;
        if (remoteCallArgs.length > argOffset && remoteCallArgs[argOffset] instanceof Object[]) {
            args = (Object[]) remoteCallArgs[argOffset];
        } else {
            args = new Object[remoteCallArgs.length - argOffset];
            if (remoteCallArgs.length > argOffset) {
                System.arraycopy(remoteCallArgs, argOffset, args, 0, remoteCallArgs.length - argOffset);
            }
        }

        String url = baseUrl.toExternalForm();
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "api/components/" + componentName + "/invoke";

        Map<String, Object> requestPayload = Map.of("methodName", methodName, "args", args);
        String jsonBody = JsonUtil.toJsonString(requestPayload);
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder().url(url).post(body).build();

        // Log the remote call
        systemContext.getLogger(this).info("Remote invocation: URL: " + url + " target component: " + componentName + " target method: " + methodName);
        Response response = client.newCall(request).execute();
        // The response body can only be consumed once. We read it into a string
        // to allow logging it in case of an error.
        final ResponseBody responseBody = response.body();
        final String responseBodyString = responseBody != null ? responseBody.string() : null;

        Long timeUsed = System.currentTimeMillis() - startTime;
        if (!response.isSuccessful()) {
            nodePerformanceTracker.track(Long.valueOf(timeUsed + NodePerformanceTracker.DEFAULT_PENALTY_MS), baseUrl); // Penalize failed calls
            throw new IOException("Remote invocation failed with HTTP code " + response.code() + " for " + url
                    + ". Body: " + responseBodyString);
        }
        nodePerformanceTracker.track(timeUsed, baseUrl);
        return (responseBodyString == null || responseBodyString.isEmpty()) ? null
                : JsonUtil.toObject(responseBodyString, Object.class);
    }
}