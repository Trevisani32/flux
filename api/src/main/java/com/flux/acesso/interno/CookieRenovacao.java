package com.flux.acesso.interno;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieRenovacao {

    private static final String NOME = "flux_renovacao";
    private static final String PATH = "/api/auth";

    private final PropriedadesAcesso propriedades;

    public CookieRenovacao(PropriedadesAcesso propriedades) {
        this.propriedades = propriedades;
    }

    public ResponseCookie criar(String token) {
        return ResponseCookie.from(NOME, token)
                .path(PATH)
                .maxAge(propriedades.renovacao().validade())
                .secure(propriedades.renovacao().cookieSeguro())
                .httpOnly(true)
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie limpar() {
        return ResponseCookie.from(NOME, "")
                .path(PATH)
                .maxAge(0)
                .secure(propriedades.renovacao().cookieSeguro())
                .httpOnly(true)
                .sameSite("Strict")
                .build();
    }
}
