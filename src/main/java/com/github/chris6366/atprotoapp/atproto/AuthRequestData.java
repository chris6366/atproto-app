package com.github.chris6366.atprotoapp.atproto;

import com.github.chris6366.atprotoapp.atproto.identity.Did;
import java.net.URI;
import java.util.List;

/**
 * @see <a
 *     href="https://github.com/bluesky-social/indigo/blob/main/atproto/auth/oauth/types.go">types.go
 *     (AuthRequestData)</a>
 */
public record AuthRequestData(
    String state,
    URI authServerUrl,
    Did accountDid,
    List<String> scopes,
    URI requestUri,
    URI tokenEndpoint,
    URI revocationEndpoint,
    String pkceVerifier,
    String dpopAuthServerNonce,
    String dpopPrivateKey) {}
