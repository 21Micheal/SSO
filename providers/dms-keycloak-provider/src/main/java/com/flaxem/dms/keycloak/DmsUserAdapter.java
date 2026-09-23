package com.flaxem.dms.keycloak;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.GroupModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

final class DmsUserAdapter extends AbstractUserAdapterFederatedStorage {
    private final DmsClient dms;
    private final DmsUser dmsUser;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean enabled;

    DmsUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, DmsClient dms, DmsUser dmsUser) {
        super(session, realm, model);
        this.dms = dms;
        this.dmsUser = dmsUser;
        this.username = dmsUser.username();
        this.email = dmsUser.email();
        this.firstName = dmsUser.firstName();
        this.lastName = dmsUser.lastName();
        this.enabled = dmsUser.enabled();
    }

    DmsUser dmsUser() {
        return dmsUser;
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(storageProviderModel, dmsUser.id());
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
        DmsUser updated = dms.updateUser(dmsUser.id(), Map.of("email", email));
        this.email = updated.email();
        this.username = updated.username();
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public void setFirstName(String firstName) {
        DmsUser updated = dms.updateUser(dmsUser.id(), Map.of("first_name", firstName == null ? "" : firstName));
        this.firstName = updated.firstName();
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setLastName(String lastName) {
        DmsUser updated = dms.updateUser(dmsUser.id(), Map.of("last_name", lastName == null ? "" : lastName));
        this.lastName = updated.lastName();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        DmsUser updated = dms.updateUser(dmsUser.id(), Map.of("enabled", enabled));
        this.enabled = updated.enabled();
    }

    @Override
    public boolean isEmailVerified() {
        return dmsUser.email_verified();
    }

    @Override
    public String getFirstAttribute(String name) {
        if ("dms_user_id".equals(name)) {
            return dmsUser.id();
        }
        return super.getFirstAttribute(name);
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        if ("dms_user_id".equals(name)) {
            return Stream.of(dmsUser.id());
        }
        return super.getAttributeStream(name);
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return Map.of("dms_user_id", List.of(dmsUser.id()));
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        return Stream.empty();
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        return Stream.empty();
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        return Stream.empty();
    }
}
