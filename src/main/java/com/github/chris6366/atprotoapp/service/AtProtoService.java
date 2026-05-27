package com.github.chris6366.atprotoapp.service;

import com.github.chris6366.atprotoapp.atproto.AtProtoClient;
import com.github.chris6366.atprotoapp.atproto.ClientSessionData;
import com.github.chris6366.atprotoapp.atproto.identity.Handle;
import com.github.chris6366.atprotoapp.config.AtProtoProperties;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AtProtoService {
  private final AtProtoClient client;
  private final AtProtoProperties props;

  public URI startAuthFlow(Handle handle) {
    return client.startAuthFlow(props.clientId(), props.redirectUri(), handle);
  }

  public ClientSessionData handleAuthorizationCallback(String code, String state, URI iss) {
    return client.handleAuthorizationCallback(
        props.clientId(), props.redirectUri(), code, state, iss);
  }

  public void logout(String did, String sessionId) {
    client.logout(props.clientId(), did, sessionId);
  }
}
