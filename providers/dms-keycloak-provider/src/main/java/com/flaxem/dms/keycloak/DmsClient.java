package com.flaxem.dms.keycloak;

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

final class DmsClient {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;

    DmsClient(String baseUrl, String apiKey) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey;
    }

    Optional<DmsUser> lookupByUsername(String username) {
        return getUser("/users/lookup/?username=" + enc(username));
    }

    Optional<DmsUser> lookupByEmail(String email) {
        return getUser("/users/lookup/?email=" + enc(email));
    }

    Optional<DmsUser> lookupById(String id) {
        return getUser("/users/lookup/?id=" + enc(id));
    }

    List<DmsUser> search(String query, int first, int max) {
        JsonNode root = request(
            "GET",
            "/users/search/?q=" + enc(query == null ? "" : query)
                + "&first=" + first
                + "&max=" + max,
            null
        );
        return JSON.convertValue(root.path("results"), new TypeReference<List<DmsUser>>() {});
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
        Map<String, Object> body = Map.of("username", username, "password", password);
        JsonNode root = request("POST", "/users/validate-password/", body);
        return root.path("valid").asBoolean(false);
    }

    DmsUser createUser(String email, String firstName, String lastName, boolean enabled) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("first_name", firstName == null ? "" : firstName);
        body.put("last_name", lastName == null ? "" : lastName);
        body.put("enabled", enabled);
        return JSON.convertValue(request("POST", "/users/", body), DmsUser.class);
    }

    DmsUser updateUser(String dmsUserId, Map<String, Object> updates) {
        return JSON.convertValue(request("PATCH", "/users/" + enc(dmsUserId) + "/", updates), DmsUser.class);
    }

    void setPassword(String dmsUserId, String password, boolean temporary) {
        request("PUT", "/users/" + enc(dmsUserId) + "/password/", Map.of(
            "password", password,
            "temporary", temporary
        ));
    }

    Map<String, Object> authorization(String dmsUserId) {
        JsonNode root = request("GET", "/users/" + enc(dmsUserId) + "/authorization/", null);
        return JSON.convertValue(root, new TypeReference<Map<String, Object>>() {});
    }

    private Optional<DmsUser> getUser(String path) {
        try {
            return Optional.of(JSON.convertValue(request("GET", path, null), DmsUser.class));
        } catch (DmsNotFoundException ignored) {
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

            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new DmsNotFoundException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                    "DMS internal API returned HTTP " + response.statusCode()
                        + " for " + method + " " + path
                        + ": " + response.body()
                );
            }
            try {
                return JSON.readTree(response.body());
            } catch (IOException e) {
                throw new IllegalStateException(
                    "DMS internal API returned invalid JSON for " + method + " " + path
                        + ": " + response.body(),
                    e
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse DMS internal API response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling DMS internal API", e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private static final class DmsNotFoundException extends RuntimeException {
    }
}
