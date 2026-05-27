package com.github.chris6366.atprotoapp.atproto;

import com.github.chris6366.atprotoapp.atproto.exception.UseDpopNonceException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.dpop.DPoPProofFactory;
import com.nimbusds.oauth2.sdk.dpop.DefaultDPoPProofFactory;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import java.io.IOException;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class TokenClient {
  TokenResponse exchangeAuthorizationCode(
      String clientId, URI redirectUri, String code, AuthRequestData info) {
    ECKey dpopKey = parseDpopKey(info.dpopPrivateKey());

    AuthorizationCodeGrant grant =
        new AuthorizationCodeGrant(
            new AuthorizationCode(code), redirectUri, new CodeVerifier(info.pkceVerifier()));

    TokenRequest request =
        new TokenRequest.Builder(info.tokenEndpoint(), new ClientID(clientId), grant).build();

    return sendTokenRequestWithNonceRetry(request, dpopKey);
  }

  void revoke(String clientId, ClientSessionData session) {
    if (session.revocationEndpoint() == null) {
      return;
    }

    ECKey dpopKey = parseDpopKey(session.dpopKey());

    revokeToken(clientId, session.revocationEndpoint(), dpopKey, session.accessToken());

    if (session.refreshToken() != null && !session.refreshToken().isBlank()) {
      revokeToken(clientId, session.revocationEndpoint(), dpopKey, session.refreshToken());
    }
  }

  private void revokeToken(String clientId, URI revocationEndpoint, ECKey dpopKey, String token) {
    HTTPRequest request = new HTTPRequest(HTTPRequest.Method.POST, revocationEndpoint);

    try {
      request.setContentType("application/x-www-form-urlencoded");
      request.appendQueryString("token=" + token + "&client_id=" + clientId);
    } catch (ParseException e) {
      throw new IllegalStateException("Failed to build revocation request", e);
    }

    sendHttpRequestWithNonceRetry(request, dpopKey);
  }

  private TokenResponse sendTokenRequestWithNonceRetry(TokenRequest tokenRequest, ECKey dpopKey) {
    HTTPRequest request = tokenRequest.toHTTPRequest();
    HTTPResponse response = sendHttpRequestWithNonceRetry(request, dpopKey);

    try {
      return TokenResponse.parse(response);
    } catch (ParseException e) {
      throw new IllegalStateException("Failed to parse token response", e);
    }
  }

  private HTTPResponse sendHttpRequestWithNonceRetry(HTTPRequest request, ECKey dpopKey) {
    try {
      DPoPProofFactory proofFactory = new DefaultDPoPProofFactory(dpopKey, JWSAlgorithm.ES256);

      try {
        return sendOnce(request, proofFactory, null);
      } catch (UseDpopNonceException e) {
        return sendOnce(request, proofFactory, e.getNonce());
      }
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to create DPoP proof factory", e);
    }
  }

  private HTTPResponse sendOnce(HTTPRequest request, DPoPProofFactory proofFactory, Nonce nonce) {
    try {
      URI htu = dpopHtu(request.getURI());

      request.setDPoP(proofFactory.createDPoPJWT(request.getMethod().name(), htu, null, nonce));

      HTTPResponse response = request.send();

      if (isDpopNonceError(response)) {
        Nonce dpopNonce = response.getDPoPNonce();
        throw new UseDpopNonceException(dpopNonce != null ? dpopNonce : new Nonce());
      }

      return response;
    } catch (IOException | JOSEException | ParseException e) {
      throw new IllegalStateException("Failed to send token endpoint request", e);
    }
  }

  private boolean isDpopNonceError(HTTPResponse response) throws ParseException {
    if (response.indicatesSuccess()) {
      return false;
    }

    TokenResponse tokenResponse = TokenResponse.parse(response);

    if (tokenResponse.indicatesSuccess()) {
      return false;
    }

    ErrorObject error = tokenResponse.toErrorResponse().getErrorObject();

    return OAuth2Error.USE_DPOP_NONCE.equals(error);
  }

  private ECKey parseDpopKey(String dpopKeyJwk) {
    try {
      return ECKey.parse(dpopKeyJwk);
    } catch (java.text.ParseException e) {
      throw new IllegalStateException("Invalid stored DPoP key", e);
    }
  }

  private static URI dpopHtu(URI uri) {
    return URI.create(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath());
  }
}
