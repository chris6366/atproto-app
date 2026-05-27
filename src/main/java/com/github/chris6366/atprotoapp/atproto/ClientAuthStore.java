package com.github.chris6366.atprotoapp.atproto;

import java.util.Optional;

/**
 * @see <a
 *     href="https://github.com/bluesky-social/indigo/blob/main/atproto/auth/oauth/store.go">store.go</a>
 */
public interface ClientAuthStore {
  Optional<ClientSessionData> getSession(String did, String sessionId);

  void saveSession(ClientSessionData session);

  void deleteSession(String did, String sessionId);

  Optional<AuthRequestData> getAuthRequestInfo(String state);

  void saveAuthRequestInfo(AuthRequestData info);

  void deleteAuthRequestInfo(String state);
}
