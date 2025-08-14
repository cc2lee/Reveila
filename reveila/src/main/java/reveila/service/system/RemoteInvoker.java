package reveila.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import reveila.error.ConfigurationException;
import reveila.system.MetaObject;
import reveila.system.Proxy;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * A system component that can invoke methods on a remote Reveila instance via its REST API.
 * This acts as a proxy, allowing any Reveila client (mobile or backend) to interact
 * with another Reveila instance, enabling clustered or distributed setups.
 */
public class RemoteInvoker extends Proxy {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private String remoteBaseUrl;
    private OkHttpClient client;
    private ObjectMapper objectMapper;

    /**
     * Constructor for instantiation by the Reveila engine.
     * @param metaObject The component's configuration metadata.
     */
    public RemoteInvoker(MetaObject metaObject) {
        super(metaObject);
        // Initialization of client and objectMapper can be done here.
        // The remoteBaseUrl will be set via setter injection.
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sets the endpoint URL for the remote Reveila instance.
     * This method is called by the Reveila engine during component initialization
     * based on the 'arguments' in the component's configuration.
     *
     * @param EndPointURL The base URL of the remote Reveila instance.
     */
    public void setEndPointURL(String EndPointURL) throws ConfigurationException {
        this.remoteBaseUrl = Objects.toString(EndPointURL, null);
        if (remoteBaseUrl == null || remoteBaseUrl.trim().isEmpty()) {
            throw new ConfigurationException("The 'EndPointURL' argument is required for the RemoteInvoker component.");
        }
    }

    /**
     * Invokes a method on a remote Reveila instance.
     */
    public Object invokeRemote(String componentName, String methodName, Object[] args) throws IOException {
        if (this.remoteBaseUrl == null) {
            throw new IllegalStateException("RemoteInvoker is not configured. The 'EndPointURL' argument is missing in the component configuration.");
        }
        String url = remoteBaseUrl + "/api/components/" + componentName + "/invoke";

        Map<String, Object> requestPayload = Map.of("methodName", methodName, "args", args);
        String jsonBody = objectMapper.writeValueAsString(requestPayload);
        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder().url(url).post(body).build();

        try (Response response = client.newCall(request).execute()) {
            // Read the body once, as it can only be consumed once.
            String responseBodyString = response.body() != null ? response.body().string() : null;

            if (!response.isSuccessful()) {
                throw new IOException("Remote invocation failed with HTTP code " + response.code() + " for " + url + ". Body: " + responseBodyString);
            }

            return (responseBodyString == null || responseBodyString.isEmpty()) ? null : objectMapper.readValue(responseBodyString, Object.class);
        }
    }
}