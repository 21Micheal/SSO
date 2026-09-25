package com.flaxem.financial.keycloak;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

final class FinancialUserAdapter extends AbstractUserAdapterFederatedStorage {
    private final FinancialClient financial;
    private final FinancialUser financialUser;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;

    FinancialUserAdapter(
        KeycloakSession session,
        RealmModel realm,
        ComponentModel model,
        FinancialClient financial,
        FinancialUser financialUser
    ) {
        super(session, realm, model);
        this.financial = financial;
        this.financialUser = financialUser;
        this.username = financialUser.username();
        this.email = financialUser.email();
        this.firstName = financialUser.firstName();
        this.lastName = financialUser.lastName();
        this.enabled = financialUser.enabled();
    }

    FinancialUser financialUser() {
        return financialUser;
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(storageProviderModel, financialUser.id());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        setEmail(username);
        this.username = username;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank.");
        }
        if (email.equals(this.email)) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("email", email);
        FinancialUser updated = financial.updateUser(financialUser.id(), updates);
        this.email = updated.email();
        this.username = updated.username();
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public void setFirstName(String firstName) {
        firstName = firstName == null ? "" : firstName;
        if (firstName.equals(this.firstName)) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("first_name", firstName);
        FinancialUser updated = financial.updateUser(financialUser.id(), updates);
        this.firstName = updated.firstName();
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setLastName(String lastName) {
        lastName = lastName == null ? "" : lastName;
        if (lastName.equals(this.lastName)) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("last_name", lastName);
        FinancialUser updated = financial.updateUser(financialUser.id(), updates);
        this.lastName = updated.lastName();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == this.enabled) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("enabled", enabled);
        FinancialUser updated = financial.updateUser(financialUser.id(), updates);
        this.enabled = updated.enabled();
    }

    @Override
    public boolean isEmailVerified() {
        return financialUser.email_verified();
    }

    @Override
    public String getFirstAttribute(String name) {
        if ("financial_user_id".equals(name)) {
            return financialUser.id();
        }
        return super.getFirstAttribute(name);
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        if ("financial_user_id".equals(name)) {
            return Stream.of(financialUser.id());
        }
        return super.getAttributeStream(name);
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> attributes = new HashMap<>(super.getAttributes());
        attributes.put("financial_user_id", new ArrayList<>(List.of(financialUser.id())));
        return attributes;
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        return financialUser.mustChangePassword()
            ? Stream.of(UserModel.RequiredAction.UPDATE_PASSWORD.name())
            : Stream.empty();
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        // Preserve default realm roles by delegating to the base class.
        // The financial-user and financial-admin roles from the realm JSON
        // are not assigned here; they must be managed via the Keycloak admin console.
        return super.getRoleMappingsStream();
    }
}
