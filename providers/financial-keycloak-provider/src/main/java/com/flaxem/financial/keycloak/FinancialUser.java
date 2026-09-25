package com.flaxem.financial.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinancialUser(
    String id,
    String username,
    String email,
    String first_name,
    String last_name,
    boolean enabled,
    boolean email_verified,
    boolean must_change_password,
    boolean has_usable_password
) {
    public String firstName() {
        return first_name == null ? "" : first_name;
    }

    public String lastName() {
        return last_name == null ? "" : last_name;
    }

    public boolean mustChangePassword() {
        return must_change_password;
    }

    public List<String> attributes() {
        return List.of(id);
    }
}
