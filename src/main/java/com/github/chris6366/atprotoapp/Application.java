package com.github.chris6366.atprotoapp;

import com.github.chris6366.atprotoapp.config.AtProtoProperties;
import com.github.chris6366.atprotoapp.config.CookieProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AtProtoProperties.class, CookieProperties.class})
public class Application {
  static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
