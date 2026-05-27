package com.github.chris6366.atprotoapp.atproto.exception;

import com.nimbusds.openid.connect.sdk.Nonce;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class UseDpopNonceException extends RuntimeException {
  private final Nonce nonce;
}
