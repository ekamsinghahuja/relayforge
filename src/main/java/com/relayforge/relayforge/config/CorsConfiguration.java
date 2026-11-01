package com.relayforge.relayforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfiguration implements WebMvcConfigurer {
    private final String origin;
    public CorsConfiguration(@Value("${relayforge.cors.allowed-origin:http://localhost:5173}") String origin) { this.origin=origin; }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET","POST","PATCH","DELETE","OPTIONS").allowedHeaders("*");
    }
}
