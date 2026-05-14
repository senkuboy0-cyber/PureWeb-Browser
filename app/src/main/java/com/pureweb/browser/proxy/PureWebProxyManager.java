package com.pureweb.browser.proxy;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PureWeb Proxy Manager - Local HTTP/HTTPS proxy server
 * Intercepts all browser traffic for video detection
 * 
 * Works similar to super-video-downloader's ProxyManager
 */
public class PureWebProxyManager {

    private static final String TAG = "PureWebProxy";
    private static final int DEFAULT_PORT = 8888;
    
    private static PureWebProxyManager instance;
    private Context context;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private int port = DEFAULT_PORT;
    
    private String localUser;
    private String localPassword;
    
    // Traffic listener for video detection
    private TrafficListener trafficListener;
    
    private PureWebProxyManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized PureWebProxyManager getInstance(Context context) {
        if (instance == null) {
            instance = new PureWebProxyManager(context);
        }
        return instance;
    }
    
    /**
     * Start local proxy server
     */
    public boolean startProxy() {
        return startProxy(DEFAULT_PORT, "pureweb", "proxy123");
    }
    
    /**
     * Start local proxy server with credentials
     */
    public boolean startProxy(int port, String user, String password) {
        if (isRunning.get()) {
            Log.d(TAG, "Proxy already running");
            return true;
        }
        
        this.port = port;
        this.localUser = user;
        this.localPassword = password;
        
        try {
            serverSocket = new ServerSocket(port);
            executor = Executors.newCachedThreadPool();
            isRunning.set(true);
            
            Log.i(TAG, "Proxy server started on port " + port);
            
            // Start accepting connections
            acceptConnections();
            
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start proxy: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop local proxy server
     */
    public void stopProxy() {
        isRunning.set(false);
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        if (executor != null) {
            executor.shutdown();
        }
        
        Log.i(TAG, "Proxy server stopped");
    }
    
    /**
     * Check if proxy is running
     */
    public boolean isProxyRunning() {
        return isRunning.get() && serverSocket != null && !serverSocket.isClosed();
    }
    
    /**
     * Get proxy port
     */
    public int getProxyPort() {
        return port;
    }
    
    /**
     * Get proxy credentials
     */
    public String[] getCredentials() {
        return new String[]{ localUser, localPassword };
    }
    
    /**
     * Set traffic listener for video detection
     */
    public void setTrafficListener(TrafficListener listener) {
        this.trafficListener = listener;
    }
    
    /**
     * Accept incoming connections
     */
    private void acceptConnections() {
        executor.execute(() -> {
            while (isRunning.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(30000);
                    
                    // Handle each connection in a new thread
                    executor.execute(new ProxyConnectionHandler(
                            clientSocket, 
                            trafficListener,
                            localUser,
                            localPassword
                    ));
                    
                } catch (IOException e) {
                    if (isRunning.get()) {
                        Log.e(TAG, "Error accepting connection: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    /**
     * Traffic listener interface
     */
    public interface TrafficListener {
        void onRequest(String method, String url, String headers, byte[] body);
        void onResponse(String url, int statusCode, String headers, byte[] body);
        void onError(String error);
    }
    
    /**
     * Inner class: Proxy Connection Handler
     */
    private static class ProxyConnectionHandler implements Runnable {
        
        private Socket clientSocket;
        private TrafficListener listener;
        private String proxyUser;
        private String proxyPassword;
        
        ProxyConnectionHandler(Socket socket, TrafficListener listener, String user, String password) {
            this.clientSocket = socket;
            this.listener = listener;
            this.proxyUser = user;
            this.proxyPassword = password;
        }
        
        @Override
        public void run() {
            try {
                // Read request from client
                RequestHandler requestHandler = new RequestHandler(clientSocket);
                RequestData requestData = requestHandler.readRequest();
                
                if (requestData == null) {
                    clientSocket.close();
                    return;
                }
                
                // Notify listener about the request
                if (listener != null) {
                    listener.onRequest(
                            requestData.method,
                            requestData.url,
                            requestData.headers,
                            requestData.body
                    );
                }
                
                // Forward request to target server
                ResponseData responseData = forwardRequest(requestData);
                
                // Notify listener about the response
                if (listener != null && responseData != null) {
                    listener.onResponse(
                            requestData.url,
                            responseData.statusCode,
                            responseData.headers,
                            responseData.body
                    );
                }
                
                // Send response back to client
                if (responseData != null) {
                    requestHandler.sendResponse(responseData);
                }
                
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError(e.getMessage());
                }
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        private ResponseData forwardRequest(RequestData requestData) {
            try {
                java.net.URL url = new java.net.URL(requestData.url);
                java.net.HttpURLConnection connection = 
                        (java.net.HttpURLConnection) url.openConnection();
                
                connection.setRequestMethod(requestData.method);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setDoInput(true);
                
                // Add headers
                for (String key : requestData.headersMap.keySet()) {
                    connection.setRequestProperty(key, requestData.headersMap.get(key));
                }
                
                // Add authentication if needed
                if (proxyUser != null && proxyPassword != null) {
                    String auth = android.util.Base64.encodeToString(
                            (proxyUser + ":" + proxyPassword).getBytes(),
                            android.util.Base64.NO_WRAP
                    );
                    connection.setRequestProperty("Proxy-Authorization", "Basic " + auth);
                }
                
                // Write body if present
                if (requestData.body != null && requestData.body.length > 0) {
                    connection.setDoOutput(true);
                    connection.getOutputStream().write(requestData.body);
                }
                
                connection.connect();
                
                // Read response
                int statusCode = connection.getResponseCode();
                StringBuilder responseHeaders = new StringBuilder();
                for (String key : connection.getHeaderFields().keySet()) {
                    if (key != null) {
                        responseHeaders.append(key).append(": ")
                                .append(connection.getHeaderField(key)).append("\r\n");
                    }
                }
                
                byte[] responseBody = null;
                try {
                    java.io.InputStream is = connection.getInputStream();
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    responseBody = baos.toByteArray();
                    is.close();
                } catch (Exception e) {
                    // Ignore
                }
                
                return new ResponseData(statusCode, responseHeaders.toString(), responseBody);
                
            } catch (Exception e) {
                return new ResponseData(502, "Bad Gateway\r\n", null);
            }
        }
    }
    
    /**
     * Request data holder
     */
    static class RequestData {
        String method;
        String url;
        String headers;
        byte[] body;
        java.util.Map<String, String> headersMap = new java.util.HashMap<>();
    }
    
    /**
     * Response data holder
     */
    static class ResponseData {
        int statusCode;
        String headers;
        byte[] body;
        
        ResponseData(int statusCode, String headers, byte[] body) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }
    }
    
    /**
     * Inner class: Request Handler
     */
    static class RequestHandler {
        private Socket socket;
        
        RequestHandler(Socket socket) {
            this.socket = socket;
        }
        
        RequestData readRequest() throws IOException {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(socket.getInputStream()));
            
            String line = reader.readLine();
            if (line == null) return null;
            
            RequestData data = new RequestData();
            
            // Parse request line
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                data.method = parts[0];
                String path = parts[1];
                
                // Handle CONNECT for HTTPS
                if (data.method.equals("CONNECT")) {
                    data.url = "https://" + path;
                } else if (path.startsWith("http")) {
                    data.url = path;
                } else {
                    // Relative path - need Host header
                    data.url = path;
                }
            }
            
            // Parse headers
            StringBuilder headersBuilder = new StringBuilder();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                headersBuilder.append(headerLine).append("\r\n");
                
                // Parse header into map
                int colonIndex = headerLine.indexOf(":");
                if (colonIndex > 0) {
                    String key = headerLine.substring(0, colonIndex).trim();
                    String value = headerLine.substring(colonIndex + 1).trim();
                    data.headersMap.put(key, value);
                }
            }
            data.headers = headersBuilder.toString();
            
            // Read body if present (POST, PUT)
            if (data.method.equals("POST") || data.method.equals("PUT")) {
                String contentLength = data.headersMap.get("Content-Length");
                if (contentLength != null) {
                    int length = Integer.parseInt(contentLength);
                    if (length > 0 && length < 1024 * 1024) { // Max 1MB
                        char[] bodyChars = new char[length];
                        reader.read(bodyChars, 0, length);
                        data.body = new String(bodyChars).getBytes();
                    }
                }
            }
            
            return data;
        }
        
        void sendResponse(ResponseData response) throws IOException {
            java.io.OutputStream out = socket.getOutputStream();
            
            String statusLine;
            if (response.statusCode == 200) {
                statusLine = "HTTP/1.1 200 Connection established\r\n";
            } else {
                statusLine = "HTTP/1.1 " + response.statusCode + " OK\r\n";
            }
            
            out.write(statusLine.getBytes());
            
            if (response.headers != null) {
                out.write(response.headers.getBytes());
            }
            
            if (response.body != null && response.body.length > 0) {
                out.write(("\r\n").getBytes());
                out.write(response.body);
            }
            
            out.flush();
        }
    }
}