package com.github.chris6366.atprotoapp.persistence;

import com.github.chris6366.atprotoapp.atproto.AuthRequestData;
import com.github.chris6366.atprotoapp.atproto.ClientAuthStore;
import com.github.chris6366.atprotoapp.atproto.ClientSessionData;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * @see <a
 *     href="https://github.com/bluesky-social/indigo/blob/main/atproto/auth/oauth/memstore.go">memstore.go</a>
 */
@Component
public class InMemoryClientAuthStore implements ClientAuthStore {
  private final ConcurrentHashMap<String, AuthRequestData> requests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ClientSessionData> sessions = new ConcurrentHashMap<>();

  @Override
  public Optional<ClientSessionData> getSession(String did, String sessionId) {
    return Optional.ofNullable(sessions.get(memKey(did, sessionId)));
  }

  @Override
  public void saveSession(ClientSessionData session) {
    sessions.put(memKey(session.accountDid().value(), session.sessionId()), session);
  }

  @Override
  public void deleteSession(String did, String sessionId) {
    sessions.remove(memKey(did, sessionId));
  }

  @Override
  public Optional<AuthRequestData> getAuthRequestInfo(String state) {
    return Optional.ofNullable(requests.get(state));
  }

  @Override
  public void saveAuthRequestInfo(AuthRequestData info) {
    AuthRequestData previous = requests.putIfAbsent(info.state(), info);

    if (previous != null) {
      throw new IllegalStateException("Auth request already saved for state: " + info.state());
    }
  }

  @Override
  public void deleteAuthRequestInfo(String state) {
    requests.remove(state);
  }

  private static String memKey(String did, String sessionId) {
    return did + "/" + sessionId;
  }
}
