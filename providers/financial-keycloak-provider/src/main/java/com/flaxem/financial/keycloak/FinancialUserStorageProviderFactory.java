package com.flaxem.financial.keycloak;

import java.util.List;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;

public final class FinancialUserStorageProviderFactory
    implements UserStorageProviderFactory<FinancialUserStorageProvider> {

    private static final Logger LOG = Logger.getLogger(FinancialUserStorageProviderFactory.class);
    static final String PROVIDER_ID = "financial-user-storage";
    static final String CONFIG_BASE_URL = "financialInternalApiBaseUrl";
    static final String CONFIG_API_KEY = "financialInternalApiKey";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public FinancialUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        String baseUrl = configValue(model, CONFIG_BASE_URL, env("FINANCIAL_INTERNAL_API_BASE_URL"));
        String apiKey = configValue(model, CONFIG_API_KEY, env("FINANCIAL_INTERNAL_IDP_API_KEY"));
        return new FinancialUserStorageProvider(session, model, new FinancialClient(baseUrl, apiKey));
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty baseUrl = new ProviderConfigProperty();
        baseUrl.setName(CONFIG_BASE_URL);
        baseUrl.setLabel("Financial internal API base URL");
        baseUrl.setType(ProviderConfigProperty.STRING_TYPE);
        baseUrl.setDefaultValue("http://financial-backend:8001/api/v1/internal/idp");
        baseUrl.setHelpText("Base URL for the financial-system internal IdP API.");

        ProviderConfigProperty apiKey = new ProviderConfigProperty();
        apiKey.setName(CONFIG_API_KEY);
        apiKey.setLabel("Financial internal API key");
        apiKey.setType(ProviderConfigProperty.PASSWORD);
        apiKey.setHelpText("Shared bearer secret configured as FINANCIAL_INTERNAL_IDP_API_KEY.");

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

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel model) {
        String baseUrl = configValue(model, CONFIG_BASE_URL, env("FINANCIAL_INTERNAL_API_BASE_URL"));
        String apiKey = configValue(model, CONFIG_API_KEY, env("FINANCIAL_INTERNAL_IDP_API_KEY"));

        if (baseUrl == null || baseUrl.isBlank()) {
            LOG.warn("Financial internal API base URL is not configured; falling back to env var.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            LOG.warn("Financial internal API key is not configured; falling back to env var.");
        }
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
