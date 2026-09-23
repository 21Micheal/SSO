package com.flaxem.dms.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

public final class DmsUserStorageProviderFactory implements UserStorageProviderFactory<DmsUserStorageProvider> {
    static final String PROVIDER_ID = "dms-user-storage";
    static final String CONFIG_BASE_URL = "dmsInternalApiBaseUrl";
    static final String CONFIG_API_KEY = "dmsInternalApiKey";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public DmsUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        String baseUrl = configValue(model, CONFIG_BASE_URL, env("DMS_INTERNAL_API_BASE_URL"));
        String apiKey = configValue(model, CONFIG_API_KEY, env("DMS_INTERNAL_IDP_API_KEY"));
        return new DmsUserStorageProvider(session, model, new DmsClient(baseUrl, apiKey));
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty baseUrl = new ProviderConfigProperty();
        baseUrl.setName(CONFIG_BASE_URL);
        baseUrl.setLabel("DMS internal API base URL");
        baseUrl.setType(ProviderConfigProperty.STRING_TYPE);
        baseUrl.setDefaultValue("http://backend:8000/api/v1/internal/idp");
        baseUrl.setHelpText("Base URL for the DMS internal IdP API.");

        ProviderConfigProperty apiKey = new ProviderConfigProperty();
        apiKey.setName(CONFIG_API_KEY);
        apiKey.setLabel("DMS internal API key");
        apiKey.setType(ProviderConfigProperty.PASSWORD);
        apiKey.setHelpText("Shared bearer secret configured as DMS_INTERNAL_IDP_API_KEY in DMS.");

        return List.of(baseUrl, apiKey);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    private static String configValue(ComponentModel model, String key, String fallback) {
        String value = model.getConfig().getFirst(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value;
    }
}
