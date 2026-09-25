package com.flaxem.financial.keycloak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FinancialClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    private final String baseUrl;
    private final String apiKey;

    FinancialClient(String baseUrl, String apiKey) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    Optional<FinancialUser> lookupByUsername(String username) {
        return getUser("/users/lookup/?username=" + enc(username));
    }

    Optional<FinancialUser> lookupByEmail(String email) {
        return getUser("/users/lookup/?email=" + enc(email));
    }

    Optional<FinancialUser> lookupById(String id) {
        return getUser("/users/lookup/?id=" + enc(id));
    }

    List<FinancialUser> search(String query, int first, int max) {
        JsonNode root = request(
            "GET",
            "/users/search/?q=" + enc(query == null ? "" : query)
                + "&first=" + first
                + "&max=" + max,
            null
        );
        return JSON.convertValue(root.path("results"), new TypeReference<List<FinancialUser>>() {});
    }

    int count(String query) {
        JsonNode root = request(
            "GET",
            "/users/search/?q=" + enc(query == null ? "" : query) + "&first=0&max=1",
            null
        );
        return root.path("count").asInt(0);
    }

    boolean validatePassword(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        JsonNode root = request("POST", "/users/validate-password/", body);
        return root.path("valid").asBoolean(false);
    }

    FinancialUser createUser(String email, String firstName, String lastName, boolean enabled) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("first_name", firstName == null ? "" : firstName);
        body.put("last_name", lastName == null ? "" : lastName);
        body.put("enabled", enabled);
        return JSON.convertValue(request("POST", "/users/", body), FinancialUser.class);
    }

    FinancialUser updateUser(String userId, Map<String, Object> updates) {
        return JSON.convertValue(request("PATCH", "/users/" + enc(userId) + "/", updates), FinancialUser.class);
    }

    void setPassword(String userId, String password, boolean temporary) {
        Map<String, Object> body = new HashMap<>();
        body.put("password", password);
        body.put("temporary", temporary);
        request("PUT", "/users/" + enc(userId) + "/password/", body);
    }

    Map<String, Object> authorization(String userId) {
        JsonNode root = request("GET", "/users/" + enc(userId) + "/authorization/", null);
        return JSON.convertValue(root, new TypeReference<Map<String, Object>>() {});
    }

    private Optional<FinancialUser> getUser(String path) {
        try {
            return Optional.of(JSON.convertValue(request("GET", path, null), FinancialUser.class));
        } catch (FinancialNotFoundException ignored) {
            return Optional.empty();
        }
    }

    private JsonNode request(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json");

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }

            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new FinancialNotFoundException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                    "Financial internal API returned HTTP " + response.statusCode()
                        + " for " + method + " " + path
                        + ": " + response.body()
                );
            }
            try {
                return JSON.readTree(response.body());
            } catch (IOException e) {
                throw new IllegalStateException(
                    "Financial internal API returned invalid JSON for " + method + " " + path
                        + ": " + response.body(),
                    e
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse financial internal API response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling financial internal API", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static final class FinancialNotFoundException extends RuntimeException {
    }
}
