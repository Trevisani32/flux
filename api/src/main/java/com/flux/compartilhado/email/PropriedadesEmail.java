package com.flux.compartilhado.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.email")
public record PropriedadesEmail(String remetente) {

    public PropriedadesEmail {
        remetente = remetente == null ? "" : remetente.strip();
        if (!remetente.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException(
                    "app.email.remetente (APP_EMAIL_REMETENTE) precisa ser um endereço de e-mail");
        }
    }
}
