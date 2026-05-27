package com.github.chris6366.atprotoapp.atproto;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.github.chris6366.atprotoapp.atproto.exception.UseDpopNonceException;
import com.github.chris6366.atprotoapp.atproto.identity.Handle;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import com.nimbusds.oauth2.sdk.dpop.DPoPProofFactory;
import com.nimbusds.oauth2.sdk.dpop.DefaultDPoPProofFactory;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.Nonce;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Performs RFC 9126 Pushed Authorization Requests (PAR).
 *
 * @see <a href="https://atproto.com/specs/oauth#pushed-authorization-requests-par">Pushed
 *     Authorization Requests (PAR)</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9126">RFC 9126</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc9449">RFC 9449</a>
 */
@Service
@RequiredArgsConstructor
public class PushedAuthorizationClient {
  private final ClientAuthStore authStore;

  public URI startAuthFlow(
      String clientId, URI redirectUri, AuthorizationServerMetadata metadata, Handle handle) {
    ECKey dpopKey;
    try {
      dpopKey = new ECKeyGenerator(Curve.P_256).keyID(UUID.randomUUID().toString()).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to generate DPoP key", e);
    }

    State state = new State();
    CodeVerifier verifier = new CodeVerifier();

    AuthorizationRequest authorizationRequest =
        new AuthorizationRequest.Builder(new ResponseType("code"), new ClientID(clientId))
            .scope(new Scope("atproto"))
            .redirectionURI(redirectUri)
            .state(state)
            .codeChallenge(verifier, CodeChallengeMethod.S256)
            .customParameter("login_hint", handle.value())
            .build();

    PushedAuthorizationResult parResult =
        pushAuthorizationRequest(
            metadata.getPushedAuthorizationRequestEndpointURI(), dpopKey, authorizationRequest);

    AuthRequestData authRequestData =
        new AuthRequestData(
            state.getValue(),
            URI.create(metadata.getIssuer().getValue()),
            null,
            List.of("atproto"),
            parResult.response().getRequestURI(),
            metadata.getTokenEndpointURI(),
            metadata.getRevocationEndpointURI(),
            verifier.getValue(),
            parResult.dpopNonce() == null ? null : parResult.dpopNonce(),
            dpopKey.toJSONString());

    authStore.saveAuthRequestInfo(authRequestData);

    return UriComponentsBuilder.fromUri(metadata.getAuthorizationEndpointURI())
        .queryParam("client_id", UriUtils.encode(clientId, UTF_8))
        .queryParam(
            "request_uri", UriUtils.encode(parResult.response().getRequestURI().toString(), UTF_8))
        .build(true)
        .toUri();
  }

  private static PushedAuthorizationResult pushAuthorizationRequest(
      URI parEndpoint, ECKey dpopKey, AuthorizationRequest authorizationRequest) {
    PushedAuthorizationRequest par =
        new PushedAuthorizationRequest(parEndpoint, authorizationRequest);

    try {
      DPoPProofFactory proofFactory = new DefaultDPoPProofFactory(dpopKey, JWSAlgorithm.ES256);

      try {
        return pushAuthorizationRequest(par, proofFactory, null);
      } catch (UseDpopNonceException e) {
        return pushAuthorizationRequest(par, proofFactory, e.getNonce());
      }
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to create DPoP proof factory", e);
    }
  }

  private static PushedAuthorizationResult pushAuthorizationRequest(
      PushedAuthorizationRequest par, DPoPProofFactory proofFactory, Nonce nonce) {
    try {
      HTTPRequest request = par.toHTTPRequest();

      request.setDPoP(
          proofFactory.createDPoPJWT(request.getMethod().name(), request.getURI(), null, nonce));

      HTTPResponse httpResponse = request.send();
      PushedAuthorizationResponse response = PushedAuthorizationResponse.parse(httpResponse);

      if (response.indicatesSuccess()) {
        return new PushedAuthorizationResult(
            response.toSuccessResponse(), getDpopNonce(httpResponse));
      }

      ErrorObject error = response.toErrorResponse().getErrorObject();

      if (OAuth2Error.USE_DPOP_NONCE.equals(error)) {
        Nonce dpopNonce = httpResponse.getDPoPNonce();
        throw new UseDpopNonceException(dpopNonce != null ? dpopNonce : new Nonce());
      }

      throw new IllegalStateException("Pushed authorization request failed: " + error);
    } catch (IOException | ParseException | JOSEException e) {
      throw new IllegalStateException("Failed to send pushed authorization request", e);
    }
  }

  private static String getDpopNonce(HTTPResponse response) {
    Nonce nonce = response.getDPoPNonce();
    return nonce == null ? null : nonce.getValue();
  }

  private record PushedAuthorizationResult(
      PushedAuthorizationSuccessResponse response, String dpopNonce) {}
}
