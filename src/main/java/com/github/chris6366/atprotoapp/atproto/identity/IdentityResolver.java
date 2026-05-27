package com.github.chris6366.atprotoapp.atproto.identity;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

/**
 * Resolves a DNS name to a Resource Server (PDS) URL. This isn't OAuth but specific to the AT
 * Protocol, so no Nimbus here. Steps:
 *
 * <ol>
 *   <li>Resolves DNS name to DID using either DNS or HTTP (since the spec states both need to be
 *       supported)
 *   <li>Resolves DID to DID document (using either {@code did:plc} or {@code did:web})
 *   <li>Extracts Resource Server (PDS) URL from DID document
 * </ol>
 *
 * <pre>
 * ┌──────────────────┐                 ┌───────────────┐
 * │ DNS name         ├──resolves to──→ │ DID           │
 * │ (alice.host.com) │                 │ (did:plc:...) │
 * └──────────────────┘                 └─────┬─────────┘
 *        ↑                                   │
 *        │                               resolves to
 *        │                                   │
 *        │                                   ↓
 *        │                            ┌───────────────┐
 *        └───────────references───────┤ DID Document  │
 *                                     │ {"id":"..."}  │
 *                                     └───────────────┘
 * </pre>
 *
 * @see <a href="https://atproto.com/guides/identity">Identity</a>
 */
@Component
public class IdentityResolver {
  private final RestClient restClient;

  public IdentityResolver(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.build();
  }

  public Optional<URI> resolvePdsUrl(Handle handle) {
    return resolveHandle(handle).flatMap(this::extractPdsUrl);
  }

  public Optional<DidDocument> resolveHandle(Handle handle) {
    return resolveHandleToDid(handle).flatMap(this::resolveDid);
  }

  /*
   * Resolve DNS name (handle) to DID
   *
   * https://atproto.com/guides/identity#handle-resolution
   */
  public Optional<Did> resolveHandleToDid(Handle handle) {
    return resolveHandleDns(handle).or(() -> resolveHandleHttp(handle));
  }

  /*
   * Resolve DID to DID document
   *
   * https://atproto.com/specs/did
   * https://web.plc.directory/spec/v0.1/did-plc
   * https://w3c-ccg.github.io/did-method-web/
   */
  public Optional<DidDocument> resolveDid(Did did) {
    return switch (did.method()) {
      case "plc" -> resolveDidPlc(did);
      case "web" -> resolveDidWeb(did);
      default -> throw new IllegalArgumentException("Unsupported DID method: " + did.method());
    };
  }

  private Optional<Did> resolveHandleDns(Handle handle) {
    try {
      String queryName = "_atproto." + handle.value() + ".";
      Lookup lookup = new Lookup(queryName, Type.TXT);
      org.xbill.DNS.Record[] records = lookup.run();

      if (records == null) return Optional.empty();
      return parseTxtResponse(records);

    } catch (TextParseException e) {
      return Optional.empty();
    }
  }

  private static Optional<Did> parseTxtResponse(Record[] records) {
    List<String> dids =
        Arrays.stream(records)
            .filter(TXTRecord.class::isInstance)
            .map(TXTRecord.class::cast)
            .flatMap(record -> record.getStrings().stream())
            .filter(value -> value.startsWith("did="))
            .map(value -> value.substring("did=".length()))
            .distinct()
            .toList();

    if (dids.isEmpty()) {
      return Optional.empty();
    }

    if (dids.size() > 1) {
      throw new IllegalStateException("Multiple different DID TXT records found");
    }

    return Optional.of(new Did(dids.getFirst()));
  }

  private Optional<Did> resolveHandleHttp(Handle handle) {
    try {
      String did =
          restClient
              .get()
              .uri("https://" + handle.value() + "/.well-known/atproto-did")
              .retrieve()
              .body(String.class);

      if (did == null || did.isBlank()) {
        return Optional.empty();
      }

      return Optional.of(new Did(did));
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private Optional<DidDocument> resolveDidPlc(Did did) {
    try {
      DidDocument didDocument =
          restClient
              .get()
              .uri("https://plc.directory/{did}", did.value())
              .retrieve()
              .body(DidDocument.class);

      return Optional.ofNullable(didDocument);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private Optional<DidDocument> resolveDidWeb(Did did) {
    URI didDocumentUri = createDidWebDocUri(did);

    try {
      DidDocument didDocument =
          restClient.get().uri(didDocumentUri).retrieve().body(DidDocument.class);

      if (didDocument == null) {
        return Optional.empty();
      }

      if (!did.equals(didDocument.id())) {
        throw new IllegalStateException(
            "did:web resolved DID document with different ID: "
                + didDocument.id()
                + " expected: "
                + did);
      }

      return Optional.of(didDocument);

    } catch (RestClientResponseException e) {
      if (e.getStatusCode().is4xxClientError()) {
        return Optional.empty();
      }
      throw e;
    }
  }

  private URI createDidWebDocUri(Did did) {
    if (did.value().contains("/")) {
      throw new IllegalArgumentException("did:web contains invalid path separators");
    }

    String withoutPrefix = did.value().substring("did:web:".length());
    String decoded = URLDecoder.decode(withoutPrefix.replace(':', '/'), StandardCharsets.UTF_8);

    if (decoded.endsWith("/") || decoded.contains("//")) {
      throw new IllegalArgumentException("did:web contains empty path segments");
    }

    String url =
        decoded.contains("/")
            ? "https://" + decoded + "/did.json"
            : "https://" + decoded + "/.well-known/did.json";

    try {
      return new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("did:web translates to invalid URI", e);
    }
  }

  /*
   * Extract PDS URL from DID document
   *
   * https://atproto.com/specs/did#did-documents
   */
  private Optional<URI> extractPdsUrl(DidDocument didDocument) {
    if (didDocument.service() == null) return Optional.empty();

    return didDocument.service().stream()
        .filter(service -> service.id().endsWith("#atproto_pds"))
        .filter(service -> "AtprotoPersonalDataServer".equals(service.type()))
        .map(DocService::serviceEndpoint)
        .map(URI::create)
        .findFirst();
  }
}
