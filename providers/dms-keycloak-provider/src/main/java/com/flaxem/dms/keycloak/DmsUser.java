package com.flaxem.dms.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DmsUser(
    String id,
    String username,
    String email,
    String first_name,
    String last_name,
    boolean enabled,
    boolean email_verified,
    boolean must_change_password,
    boolean has_usable_password,
    String role,
    String organization_id,
    boolean is_staff,
    boolean is_superuser
) {
    public String firstName() {
        return first_name == null ? "" : first_name;
    }

    public String lastName() {
        return last_name == null ? "" : last_name;
    }

    public List<String> attributes() {
        return List.of(id);
    }
}
