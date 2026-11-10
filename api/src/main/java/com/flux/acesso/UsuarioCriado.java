package com.flux.acesso;

/**
 * Um usuário novo foi gravado (cadastro público ou usuário inicial). É publicado dentro
 * da transação que grava o usuário: quem reage de forma síncrona grava junto com ele.
 */
public record UsuarioCriado(Long usuarioId) {
}
