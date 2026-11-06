package com.flux.compartilhado.seguranca;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class UsuarioAtual {

    public Long id() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao instanceof JwtAuthenticationToken jwtAuthentication) {
            return Long.valueOf(jwtAuthentication.getToken().getSubject());
        }
        throw new IllegalStateException("Nenhum usuário autenticado");
    }
}
