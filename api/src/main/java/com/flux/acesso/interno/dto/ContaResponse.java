package com.flux.acesso.interno.dto;

import java.time.Instant;

/** GET /api/usuarios/me. trocaDeEmailPendente e termos podem ser null. */
public record ContaResponse(Long id, String nome, String email, TrocaDeEmailPendente trocaDeEmailPendente,
                            TermosAceitos termos) {

    public record TrocaDeEmailPendente(String email, Instant expiraEm) {
    }

    public record TermosAceitos(String versao, Instant aceitosEm) {
    }
}
