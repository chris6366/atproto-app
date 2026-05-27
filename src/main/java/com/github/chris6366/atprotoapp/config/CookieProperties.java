package com.github.chris6366.atprotoapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.cookie")
public record CookieProperties(boolean secure) {}
