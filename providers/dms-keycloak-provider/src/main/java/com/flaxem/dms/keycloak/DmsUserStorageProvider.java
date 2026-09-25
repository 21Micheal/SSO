package com.flaxem.dms.keycloak;

import java.util.Map;
import java.util.stream.Stream;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;

/**
 * Lookup-only. Do not enable this as the realm identity backend — financial-user-storage
 * owns passwords. Kept so the live mapper can resolve DMS users by email.
 */
public final class DmsUserStorageProvider implements
    UserStorageProvider,
    UserLookupProvider,
    UserQueryProvider {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final DmsClient dms;

    DmsUserStorageProvider(KeycloakSession session, ComponentModel model, DmsClient dms) {
        this.session = session;
        this.model = model;
        this.dms = dms;
    }

    @Override
    public void close() {
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        return dms.lookupById(externalId).map(user -> adapter(realm, user)).orElse(null);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return dms.lookupByUsername(username).map(user -> adapter(realm, user)).orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return dms.lookupByEmail(email).map(user -> adapter(realm, user)).orElse(null);
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        return dms.count("");
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return getUsersCount(realm);
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return dms.count(search);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return dms.count(params.getOrDefault(UserModel.SEARCH, ""));
    }

    public Stream<UserModel> getUsersStream(RealmModel realm, Integer firstResult, Integer maxResults) {
        return searchForUserStream(realm, "", firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserStream(
        RealmModel realm,
        String search,
        Integer firstResult,
        Integer maxResults
    ) {
        int first = firstResult == null ? 0 : firstResult;
        int max = maxResults == null ? 20 : maxResults;
        try {
            return dms.search(search, first, max).stream().map(user -> adapter(realm, user));
        } catch (RuntimeException exc) {
            // Log error but don't propagate - DMS is optional for identity
            return Stream.empty();
        }
    }

    @Override
    public Stream<UserModel> searchForUserStream(
        RealmModel realm,
        Map<String, String> params,
        Integer firstResult,
        Integer maxResults
    ) {
        String search = params.getOrDefault(
            UserModel.SEARCH,
            params.getOrDefault(UserModel.EMAIL, params.getOrDefault(UserModel.USERNAME, ""))
        );
        return searchForUserStream(realm, search, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(
        RealmModel realm,
        org.keycloak.models.GroupModel group,
        Integer firstResult,
        Integer maxResults
    ) {
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(
        RealmModel realm,
        String attrName,
        String attrValue
    ) {
        if ("dms_user_id".equals(attrName)) {
            return dms.lookupById(attrValue).stream().map(user -> adapter(realm, user));
        }
        return Stream.empty();
    }

    private UserModel adapter(RealmModel realm, DmsUser user) {
        return new DmsUserAdapter(session, realm, model, user);
    }
}
