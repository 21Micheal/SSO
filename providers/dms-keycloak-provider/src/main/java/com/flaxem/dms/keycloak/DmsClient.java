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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Claims-only client. Identity (passwords, user creation) lives in the financial system.
 *
 * One HttpClient is shared for the whole JVM; instances only hold base URL + key.
 */
final class DmsClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    private final String baseUrl;
    private final String apiKey;

    DmsClient(String baseUrl, String apiKey) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
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

    /** @throws DmsNotFoundException if DMS has no such user (HTTP 404). */
    Map<String, Object> authorization(String dmsUserId) {
        JsonNode root = request("GET", "/users/" + enc(dmsUserId) + "/authorization/", null);
        return JSON.convertValue(root, new TypeReference<Map<String, Object>>() {});
    }

    /** @throws DmsNotFoundException if DMS has no such user (HTTP 404). */
    Map<String, Object> authorizationByEmail(String email) {
        JsonNode root = request("GET", "/users/authorization/?email=" + enc(email), null);
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

            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new DmsNotFoundException();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Status only: never echo the body (it may contain user data).
                throw new IllegalStateException(
                    "DMS internal API returned HTTP " + response.statusCode()
                        + " for " + method + " " + path
                );
            }
            try {
                return JSON.readTree(response.body());
            } catch (IOException e) {
                throw new IllegalStateException(
                    "DMS internal API returned invalid JSON for " + method + " " + path, e
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not call the DMS internal API: " + e.getMessage(), e);
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

    /** DMS answered 404. Package-private so the mapper can tell "not provisioned" from "broken". */
    static final class DmsNotFoundException extends RuntimeException {
    }
}
