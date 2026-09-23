package com.flaxem.financial.keycloak;

import java.util.Map;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

public final class FinancialUserStorageProvider implements
    UserStorageProvider,
    UserLookupProvider,
    UserQueryProvider,
    UserRegistrationProvider,
    CredentialInputValidator,
    CredentialInputUpdater {

    private static final String PASSWORD_TYPE = "password";
    private static final Logger LOG = Logger.getLogger(FinancialUserStorageProvider.class);

    private final KeycloakSession session;
    private final ComponentModel model;
    private final FinancialClient financial;

    FinancialUserStorageProvider(KeycloakSession session, ComponentModel model, FinancialClient financial) {
        this.session = session;
        this.model = model;
        this.financial = financial;
    }

    @Override
    public void close() {
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        String externalId = StorageId.externalId(id);
        return financial.lookupById(externalId).map(user -> adapter(realm, user)).orElse(null);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return financial.lookupByUsername(username).map(user -> adapter(realm, user)).orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return financial.lookupByEmail(email).map(user -> adapter(realm, user)).orElse(null);
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        return financial.count("");
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return getUsersCount(realm);
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return financial.count(search);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return financial.count(params.getOrDefault(UserModel.SEARCH, ""));
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
            return financial.search(search, first, max).stream().map(user -> adapter(realm, user));
        } catch (RuntimeException exc) {
            LOG.errorf(exc, "Financial user search failed: search=%s first=%d max=%d", search, first, max);
            throw exc;
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
        if ("financial_user_id".equals(attrName)) {
            return financial.lookupById(attrValue).stream().map(user -> adapter(realm, user));
        }
        return Stream.empty();
    }

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        FinancialUser created = financial.createUser(username, "", "", true);
        return adapter(realm, created);
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        throw new UnsupportedOperationException("Delete/deactivate users in the financial system.");
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PASSWORD_TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel credential)) {
            return false;
        }
        return financial.validatePassword(user.getUsername(), credential.getChallengeResponse());
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel credential)) {
            return false;
        }
        financial.setPassword(financialId(user), credential.getChallengeResponse(), false);
        return true;
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
        throw new UnsupportedOperationException("Disable credentials in the financial system.");
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return Stream.empty();
    }

    private UserModel adapter(RealmModel realm, FinancialUser user) {
        return new FinancialUserAdapter(session, realm, model, financial, user);
    }

    private static String financialId(UserModel user) {
        if (user instanceof FinancialUserAdapter adapter) {
            return adapter.financialUser().id();
        }
        String attr = user.getFirstAttribute("financial_user_id");
        if (attr != null && !attr.isBlank()) {
            return attr;
        }
        return StorageId.externalId(user.getId());
    }
}
