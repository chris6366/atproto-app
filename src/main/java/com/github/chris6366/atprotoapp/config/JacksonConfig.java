package com.github.chris6366.atprotoapp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;

/**
 * Disable {@code SORT_PROPERTIES_ALPHABETICALLY} and {@code ORDER_MAP_ENTRIES_BY_KEYS} in order to
 * preserve the order of the fields the way they are declared in the respective POJOs (more
 * consistent with the underlying objects).
 */
@Configuration
public class JacksonConfig {
  @Bean
  JsonMapperBuilderCustomizer jacksonCustomizer() {
    return builder ->
        builder
            .changeDefaultPropertyInclusion(
                incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }
}
