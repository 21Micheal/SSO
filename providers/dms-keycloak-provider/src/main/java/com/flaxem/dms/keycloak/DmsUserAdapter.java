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
    private final DmsUser dmsUser;

    DmsUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, DmsUser dmsUser) {
        super(session, realm, model);
        this.dmsUser = dmsUser;
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
        return dmsUser.username();
    }

    @Override
    public void setUsername(String username) {
        throw new UnsupportedOperationException("DMS users are read-only in Keycloak.");
    }

    @Override
    public String getEmail() {
        return dmsUser.email();
    }

    @Override
    public void setEmail(String email) {
        throw new UnsupportedOperationException("DMS users are read-only in Keycloak.");
    }

    @Override
    public String getFirstName() {
        return dmsUser.firstName();
    }

    @Override
    public void setFirstName(String firstName) {
        throw new UnsupportedOperationException("DMS users are read-only in Keycloak.");
    }

    @Override
    public String getLastName() {
        return dmsUser.lastName();
    }

    @Override
    public void setLastName(String lastName) {
        throw new UnsupportedOperationException("DMS users are read-only in Keycloak.");
    }

    @Override
    public boolean isEnabled() {
        return dmsUser.enabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        throw new UnsupportedOperationException("DMS users are read-only in Keycloak.");
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
