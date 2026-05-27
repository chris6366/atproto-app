package com.github.chris6366.atprotoapp.atproto.discovery;

import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import com.nimbusds.oauth2.sdk.id.Issuer;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Resource Server (PDS) and Authorization Server metadata discovery.
 *
 * @see <a href="https://atproto.com/specs/oauth#authorization-servers">Authorization Servers</a>
 */
@Service
public class DiscoveryClient {
  private final RestClient restClient;

  public DiscoveryClient(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  public Optional<AuthorizationServerMetadata> getAuthServerMetadata(URI pdsUrl) {
    return getResourceServerMetadata(pdsUrl)
        .flatMap(this::getAuthorizationServer)
        .flatMap(this::getAuthorizationServerMetadata);
  }

  public Optional<ProtectedResourceMetadata> getResourceServerMetadata(URI pdsUrl) {
    URI metadataUri = pdsUrl.resolve("/.well-known/oauth-protected-resource");

    try {
      ProtectedResourceMetadata metadata =
          restClient.get().uri(metadataUri).retrieve().body(ProtectedResourceMetadata.class);

      return Optional.ofNullable(metadata);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        return Optional.empty();
      }

      throw e;
    }
  }

  public Optional<AuthorizationServerMetadata> getAuthorizationServerMetadata(URI issuer) {
    try {
      return Optional.of(AuthorizationServerMetadata.resolve(new Issuer(issuer.toString())));
    } catch (GeneralException | IOException e) {
      return Optional.empty();
    }
  }

  private Optional<URI> getAuthorizationServer(ProtectedResourceMetadata metadata) {
    if (metadata.authorizationServers() == null || metadata.authorizationServers().size() != 1) {
      return Optional.empty();
    }

    return Optional.of(metadata.authorizationServers().getFirst());
  }
}
