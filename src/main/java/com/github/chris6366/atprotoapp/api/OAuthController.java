package com.github.chris6366.atprotoapp.api;

import com.github.chris6366.atprotoapp.atproto.ClientAuthStore;
import com.github.chris6366.atprotoapp.atproto.ClientSessionData;
import com.github.chris6366.atprotoapp.atproto.identity.Handle;
import com.github.chris6366.atprotoapp.config.AtProtoProperties;
import com.github.chris6366.atprotoapp.config.CookieProperties;
import com.github.chris6366.atprotoapp.dto.LoginRequest;
import com.github.chris6366.atprotoapp.dto.LoginResponse;
import com.github.chris6366.atprotoapp.service.AtProtoService;
import com.nimbusds.oauth2.sdk.GrantType;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.ClientAuthenticationMethod;
import com.nimbusds.oauth2.sdk.client.ClientMetadata;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OAuthController {
  private final AtProtoService service;
  private final ClientAuthStore authStore;
  private final AtProtoProperties atProtoProps;
  private final CookieProperties cookieProps;

  @PostMapping("/oauth/login")
  public LoginResponse login(@RequestBody LoginRequest request) {
    URI redirectUrl = service.startAuthFlow(new Handle(request.handle()));
    return new LoginResponse(redirectUrl.toString());
  }

  @GetMapping("/oauth/callback")
  public ResponseEntity<Void> callback(
      @RequestParam String code, @RequestParam String state, @RequestParam String iss) {
    ClientSessionData session = service.handleAuthorizationCallback(code, state, URI.create(iss));

    ResponseCookie didCookie = authCookie("did", session.accountDid().value(), Duration.ofDays(7));

    ResponseCookie sessionCookie =
        authCookie("session_id", session.sessionId(), Duration.ofDays(7));

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.SET_COOKIE, didCookie.toString())
        .header(HttpHeaders.SET_COOKIE, sessionCookie.toString())
        .location(originOf(atProtoProps.redirectUri()))
        .build();
  }

  @GetMapping("/api/me")
  public ResponseEntity<?> me(
      @CookieValue(name = "did", required = false) String did,
      @CookieValue(name = "session_id", required = false) String sessionId) {
    if (isBlank(did) || isBlank(sessionId)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    return authStore
        .getSession(did, sessionId)
        .<ResponseEntity<?>>map(
            session ->
                ResponseEntity.ok(
                    Map.of(
                        "did", session.accountDid().value(),
                        "sessionId", session.sessionId())))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  @PostMapping("/oauth/logout")
  public ResponseEntity<Map<String, Boolean>> logout(
      @CookieValue(name = "did", required = false) String did,
      @CookieValue(name = "session_id", required = false) String sessionId) {

    if (!isBlank(did) && !isBlank(sessionId)) {
      service.logout(did, sessionId);
    }

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, clearCookie("did").toString())
        .header(HttpHeaders.SET_COOKIE, clearCookie("session_id").toString())
        .body(Map.of("success", true));
  }

  // https://atproto.com/specs/oauth#clients
  @GetMapping("/oauth-client-metadata.json")
  public Map<String, Object> clientMetadata() {
    ClientMetadata metadata = new ClientMetadata();
    metadata.setCustomField("client_id", atProtoProps.clientId());
    metadata.setName(atProtoProps.clientName());
    metadata.setCustomField("application_type", atProtoProps.applicationType());
    metadata.setGrantTypes(Set.of(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN));
    metadata.setScope(new Scope("atproto", "transition:generic"));
    metadata.setResponseTypes(Set.of(ResponseType.CODE));
    metadata.setRedirectionURI(atProtoProps.redirectUri());
    metadata.setTokenEndpointAuthMethod(ClientAuthenticationMethod.NONE);
    metadata.setDPoPBoundAccessTokens(true);
    return metadata.toJSONObject();
  }

  private ResponseCookie authCookie(String name, String value, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(cookieProps.secure())
        .sameSite("Lax")
        .path("/")
        .maxAge(maxAge)
        .build();
  }

  private ResponseCookie clearCookie(String name) {
    return authCookie(name, "", Duration.ZERO);
  }

  private static URI originOf(URI uri) {
    return URI.create(uri.getScheme() + "://" + uri.getAuthority() + "/");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
