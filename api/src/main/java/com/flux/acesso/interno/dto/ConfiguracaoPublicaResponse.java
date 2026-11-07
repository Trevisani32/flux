package com.flux.acesso.interno.dto;

/** GET /api/publico/configuracao. */
public record ConfiguracaoPublicaResponse(boolean cadastroPublico, Controlador controlador, String versaoDosTermos) {

    public record Controlador(String nome, String email) {
    }
}
