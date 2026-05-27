package com.github.chris6366.atprotoapp.atproto.identity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * @see <a href="https://atproto.com/specs/did">DID</a>
 * @see <a
 *     href="https://github.com/bluesky-social/indigo/blob/main/atproto/syntax/did.go">did.go</a>
 */
public record Did(String value) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public Did {}

  public String method() {
    return value.split(":", 3)[1].toLowerCase(Locale.ROOT);
  }

  public String identifier() {
    return value.split(":", 3)[2];
  }

  @JsonValue
  @Override
  public String toString() {
    return value;
  }
}
