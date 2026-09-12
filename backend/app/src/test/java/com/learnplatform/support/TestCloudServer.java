package com.learnplatform.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class TestCloudServer implements AutoCloseable {
    private final HttpServer server;
    public String response;
    public String contentType = "application/json";
    public int status = 200;
    public String request;
    public int calls;

    public TestCloudServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls++;
            request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1"; }
    @Override public void close() { server.stop(0); }
}
