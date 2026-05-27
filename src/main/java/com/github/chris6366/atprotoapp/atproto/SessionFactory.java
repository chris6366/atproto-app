package com.github.chris6366.atprotoapp.atproto;

import com.github.chris6366.atprotoapp.atproto.identity.Did;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.id.Identifier;
import com.nimbusds.oauth2.sdk.token.AccessToken;
import com.nimbusds.oauth2.sdk.token.Tokens;
import org.springframework.stereotype.Component;

@Component
public class SessionFactory {
  public ClientSessionData create(AuthRequestData info, TokenResponse tokenResponse) {
    if (!tokenResponse.indicatesSuccess()) {
      ErrorObject error = tokenResponse.toErrorResponse().getErrorObject();
      throw new IllegalStateException("Token request failed: " + error);
    }

    AccessTokenResponse successResponse = tokenResponse.toSuccessResponse();
    Tokens tokens = successResponse.getTokens();

    AccessToken accessToken = tokens.getAccessToken();

    if (accessToken == null) {
      throw new IllegalStateException("Token response is missing access token");
    }

    Scope scope = accessToken.getScope();

    if (scope == null || !scope.contains("atproto")) {
      throw new IllegalStateException("Token response does not include required atproto scope");
    }

    Object subClaim = successResponse.getCustomParameters().get("sub");

    if (!(subClaim instanceof String sub) || sub.isBlank()) {
      throw new IllegalStateException("Token response is missing sub");
    }

    return new ClientSessionData(
        new Did(sub),
        info.state(),
        null,
        info.authServerUrl(),
        info.tokenEndpoint(),
        info.revocationEndpoint(),
        scope.stream().map(Identifier::getValue).toList(),
        accessToken.getValue(),
        tokens.getRefreshToken() == null ? null : tokens.getRefreshToken().getValue(),
        info.dpopAuthServerNonce(),
        null,
        info.dpopPrivateKey());
  }
}
