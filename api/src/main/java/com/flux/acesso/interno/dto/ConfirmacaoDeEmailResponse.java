package com.flux.acesso.interno.dto;

/** tipo: "CADASTRO" ou "TROCA_DE_EMAIL"; email: o endereço que passou a valer. */
public record ConfirmacaoDeEmailResponse(String tipo, String email) {
}
