package com.github.chris6366.atprotoapp.atproto.identity;

public record DocVerificationMethod(
    String id, String type, String controller, String publicKeyMultibase) {}
