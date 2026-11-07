package com.flux.compartilhado.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropriedadesEmail.class)
public class EmailConfig {
}
