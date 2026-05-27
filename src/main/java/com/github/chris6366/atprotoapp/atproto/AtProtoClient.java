package com.github.chris6366.atprotoapp.atproto;

import com.github.chris6366.atprotoapp.atproto.discovery.DiscoveryClient;
import com.github.chris6366.atprotoapp.atproto.identity.Handle;
import com.github.chris6366.atprotoapp.atproto.identity.IdentityResolver;
import com.nimbusds.oauth2.sdk.*;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AtProtoClient {
  private final IdentityResolver identityResolver;
  private final DiscoveryClient discoveryClient;
  private final PushedAuthorizationClient pushedAuthorizationClient;
  private final TokenClient tokenClient;

  private final SessionFactory sessionFactory;
  private final ClientAuthStore authStore;

  /*
   * See Summary of Authorization Flow below to get a high-level overview of what this method is
   * supposed to do. TL;DR:
   *
   * - Resolve handle -> DID -> DID Doc -> PDS URL
   * - Fetch Authorization Server metadata from the PDS
   * - Send PAR request
   * - Return authorization URL
   *
   * https://atproto.com/specs/oauth#summary-of-authorization-flow
   */
  public URI startAuthFlow(String clientId, URI redirectUri, Handle handle) {
    return identityResolver
        .resolvePdsUrl(handle)
        .flatMap(discoveryClient::getAuthServerMetadata)
        .map(m -> pushedAuthorizationClient.startAuthFlow(clientId, redirectUri, m, handle))
        .orElseThrow(() -> new IllegalStateException("Failed to start auth flow"));
  }

  public ClientSessionData handleAuthorizationCallback(
      String clientId, URI redirectUri, String code, String state, URI issuer) {
    AuthRequestData info =
        authStore
            .getAuthRequestInfo(state)
            .orElseThrow(() -> new IllegalStateException("Unknown OAuth state"));

    authStore.deleteAuthRequestInfo(state);

    if (!info.authServerUrl().equals(issuer)) {
      throw new IllegalStateException("Issuer mismatch");
    }

    TokenResponse tokenResponse =
        tokenClient.exchangeAuthorizationCode(clientId, redirectUri, code, info);
    ClientSessionData session = sessionFactory.create(info, tokenResponse);
    authStore.saveSession(session);
    return session;
  }

  public void logout(String clientId, String did, String sessionId) {
    ClientSessionData session = authStore.getSession(did, sessionId).orElse(null);

    if (session == null) {
      return;
    }

    try {
      tokenClient.revoke(clientId, session);
    } finally {
      authStore.deleteSession(did, sessionId);
    }
  }
}
