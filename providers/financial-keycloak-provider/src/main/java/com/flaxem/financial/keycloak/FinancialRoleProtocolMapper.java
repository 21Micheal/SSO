package com.flaxem.financial.keycloak;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

public final class FinancialRoleProtocolMapper extends AbstractOIDCProtocolMapper
    implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "financial-live-authorization-mapper";
    private static final String CONFIG_BASE_URL = "financialInternalApiBaseUrl";
    private static final String CONFIG_API_KEY = "financialInternalApiKey";
    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty baseUrl = new ProviderConfigProperty();
        baseUrl.setName(CONFIG_BASE_URL);
        baseUrl.setLabel("Financial internal API base URL");
        baseUrl.setType(ProviderConfigProperty.STRING_TYPE);
        baseUrl.setDefaultValue("http://financial-backend:8001/api/v1/internal/idp");
        CONFIG_PROPERTIES.add(baseUrl);

        ProviderConfigProperty apiKey = new ProviderConfigProperty();
        apiKey.setName(CONFIG_API_KEY);
        apiKey.setLabel("Financial internal API key");
        apiKey.setType(ProviderConfigProperty.PASSWORD);
        CONFIG_PROPERTIES.add(apiKey);
    }

    @Override
    public String getDisplayCategory() {
        return "Token mapper";
    }

    @Override
    public String getDisplayType() {
        return "Financial live authorization";
    }

    @Override
    public String getHelpText() {
        return "Loads financial_role, permissions, and organization_id at token issuance.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getProtocol() {
        return OIDCLoginProtocol.LOGIN_PROTOCOL;
    }

    @Override
    protected void setClaim(
        IDToken token,
        ProtocolMapperModel mappingModel,
        UserSessionModel userSession,
        KeycloakSession keycloakSession,
        ClientSessionContext clientSessionCtx
    ) {
        String financialUserId = userSession.getUser().getFirstAttribute("financial_user_id");
        if (financialUserId == null || financialUserId.isBlank()) {
            return;
        }

        String baseUrl = configValue(mappingModel, CONFIG_BASE_URL, env("FINANCIAL_INTERNAL_API_BASE_URL"));
        String apiKey = configValue(mappingModel, CONFIG_API_KEY, env("FINANCIAL_INTERNAL_IDP_API_KEY"));
        Map<String, Object> authz = new FinancialClient(baseUrl, apiKey).authorization(financialUserId);

        token.getOtherClaims().put("financial_user_id", financialUserId);
        token.getOtherClaims().put("financial_role", authz.get("financial_role"));
        token.getOtherClaims().put("financial_permissions", authz.get("permissions"));
        token.getOtherClaims().put("organization_id", authz.get("organization_id"));
        token.getOtherClaims().put("is_staff", authz.get("is_staff"));
    }

    private static String configValue(ProtocolMapperModel model, String key, String fallback) {
        String value = model.getConfig().get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value;
    }
}
