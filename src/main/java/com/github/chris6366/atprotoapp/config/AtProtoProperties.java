package com.github.chris6366.atprotoapp.config;

import com.nimbusds.openid.connect.sdk.rp.ApplicationType;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @see <a href="https://atproto.com/specs/oauth#client-id-metadata-document">Client ID Metadata
 *     Document</a>
 */
@ConfigurationProperties(prefix = "app.atproto")
public record AtProtoProperties(
    String clientId,
    String clientName,
    URI redirectUri,
    ApplicationType applicationType,
    URI clientUri,
    URI logoUri,
    URI tosUri,
    URI policyUri) {}
