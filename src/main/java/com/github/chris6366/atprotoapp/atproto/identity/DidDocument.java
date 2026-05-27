package com.github.chris6366.atprotoapp.atproto.identity;

import java.util.List;

/**
 * @see <a href="https://atproto.com/specs/did#did-documents">DID Documents</a>
 * @see <a
 *     href="https://github.com/bluesky-social/indigo/blob/main/atproto/identity/diddoc.go">diddoc.go</a>
 */
public record DidDocument(
    Did id,
    List<String> alsoKnownAs,
    List<DocVerificationMethod> verificationMethod,
    List<DocService> service) {}
