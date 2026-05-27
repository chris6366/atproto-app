package com.github.chris6366.atprotoapp.atproto;

import com.github.chris6366.atprotoapp.atproto.identity.Did;
import java.net.URI;
import java.util.List;

/**
 * @see <a
 *     href="https://github.com/bluesky-social/indigo/blob/main/atproto/auth/oauth/session.go">session.go</a>
 */
public record ClientSessionData(
    Did accountDid,
    String sessionId,
    URI hostUrl,
    URI authServerUrl,
    URI tokenEndpoint,
    URI revocationEndpoint,
    List<String> scopes,
    String accessToken,
    String refreshToken,
    String dpopAuthServerNonce,
    String dpopHostNonce,
    String dpopKey) {}
