package com.github.chris6366.atprotoapp.atproto.discovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;

/**
 * @see <a href="https://atproto.com/specs/oauth#authorization-servers">Authorization Servers</a>
 * @see <a href="https://datatracker.ietf.org/doc/rfc9728/">RFC 9728</a>
 */
public record ProtectedResourceMetadata(
    @JsonProperty("authorization_servers") List<URI> authorizationServers) {}
