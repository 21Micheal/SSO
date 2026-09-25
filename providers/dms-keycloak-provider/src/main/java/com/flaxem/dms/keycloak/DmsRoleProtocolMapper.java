package com.flaxem.dms.keycloak;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.OAuthErrorException;
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
import org.keycloak.services.ErrorResponseException;

/**
 * Adds dms_* claims from the DMS role-only API.
 *
 *  - DMS says "no such user" (404): the user is simply not provisioned in DMS. No claims are
 *    added and the token is still issued; DMS itself denies access. This is expected.
 *  - Any other failure (timeout, 5xx, rejected key): fail closed and log, instead of silently
 *    issuing a token without dms_* claims that DMS would then deny with no trace.
 */
public final class DmsRoleProtocolMapper extends AbstractOIDCProtocolMapper
    implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "dms-live-authorization-mapper";
    private static final String CONFIG_BASE_URL = "dmsInternalApiBaseUrl";
    private static final String CONFIG_API_KEY = "dmsInternalApiKey";
    private static final Logger LOG = Logger.getLogger(DmsRoleProtocolMapper.class);
    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = new ArrayList<>();

    static {
        ProviderConfigProperty baseUrl = new ProviderConfigProperty();
        baseUrl.setName(CONFIG_BASE_URL);
        baseUrl.setLabel("DMS internal API base URL");
        baseUrl.setType(ProviderConfigProperty.STRING_TYPE);
        baseUrl.setDefaultValue("http://backend:8000/api/v1/internal/idp");
        CONFIG_PROPERTIES.add(baseUrl);

        ProviderConfigProperty apiKey = new ProviderConfigProperty();
        apiKey.setName(CONFIG_API_KEY);
        apiKey.setLabel("DMS internal API key");
        apiKey.setType(ProviderConfigProperty.PASSWORD);
        CONFIG_PROPERTIES.add(apiKey);
    }

    @Override
    public String getDisplayCategory() {
        return "Token mapper";
    }

    @Override
    public String getDisplayType() {
        return "DMS live authorization";
    }

    @Override
    public String getHelpText() {
        return "Loads live DMS role/permission claims. Identity is federated from the financial system.";
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
        String baseUrl = configValue(mappingModel, CONFIG_BASE_URL, env("DMS_INTERNAL_API_BASE_URL"));
        String apiKey = configValue(mappingModel, CONFIG_API_KEY, env("DMS_INTERNAL_IDP_API_KEY"));
        DmsClient client = new DmsClient(baseUrl, apiKey);

        String dmsUserId = userSession.getUser().getFirstAttribute("dms_user_id");
        Map<String, Object> authz;
        try {
            if (dmsUserId != null && !dmsUserId.isBlank()) {
                authz = client.authorization(dmsUserId);
            } else {
                String email = userSession.getUser().getEmail();
                if (email == null || email.isBlank()) {
                    email = userSession.getUser().getUsername();
                }
                if (email == null || email.isBlank()) {
                    LOG.debugf("User %s has no email/username; skipping DMS claims.",
                        userSession.getUser().getId());
                    return;
                }
                authz = client.authorizationByEmail(email);
                Object id = authz.get("dms_user_id");
                dmsUserId = id == null ? "" : String.valueOf(id);
            }
        } catch (DmsClient.DmsNotFoundException notProvisioned) {
            // info, not debug: a wrong base URL also 404s, and must not be invisible.
            LOG.infof("DMS reports no account for user %s (404); issuing token without dms_* claims.",
                userSession.getUser().getId());
            return;
        } catch (RuntimeException e) {
            LOG.errorf(e, "DMS authorization lookup failed for user %s; refusing to issue token.",
                userSession.getUser().getId());
            throw new ErrorResponseException(
                OAuthErrorException.SERVER_ERROR,
                "Could not load authorization from DMS.",
                Response.Status.SERVICE_UNAVAILABLE
            );
        }

        token.getOtherClaims().put("dms_user_id", dmsUserId);
        token.getOtherClaims().put("dms_role", authz.get("dms_role"));
        token.getOtherClaims().put("dms_permissions", authz.get("permissions"));
        token.getOtherClaims().put("dms_groups", authz.get("groups"));
        token.getOtherClaims().put("dms_admin", authz.get("has_admin_access"));
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
