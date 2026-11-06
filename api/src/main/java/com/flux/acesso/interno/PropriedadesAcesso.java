package com.flux.acesso.interno;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record PropriedadesAcesso(PropriedadesJwt jwt, PropriedadesRenovacao renovacao,
                                 PropriedadesUsuarioInicial usuarioInicial) {

    public record PropriedadesJwt(String segredo, Duration validadeAcesso) {

        public PropriedadesJwt {
            if (segredo == null || segredo.getBytes(UTF_8).length < 32) {
                throw new IllegalArgumentException(
                        "app.jwt.segredo (JWT_SEGREDO) precisa ter pelo menos 32 bytes");
            }
            if (validadeAcesso == null || validadeAcesso.isZero() || validadeAcesso.isNegative()) {
                throw new IllegalArgumentException("app.jwt.validade-acesso precisa ser positiva");
            }
        }
    }

    public record PropriedadesRenovacao(Duration validade, boolean cookieSeguro) {

        public PropriedadesRenovacao {
            if (validade == null || validade.isZero() || validade.isNegative()) {
                throw new IllegalArgumentException("app.renovacao.validade precisa ser positiva");
            }
        }
    }

    public record PropriedadesUsuarioInicial(String email, String senha, String nome) {
    }
}
