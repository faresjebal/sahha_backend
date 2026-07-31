package com.sahha.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthSessionCacheProperties.class)
public class AuthSessionCacheConfiguration {
}
