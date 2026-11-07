package com.flux.acesso.interno;

import java.util.UUID;

/** Pediram a troca do e-mail: o endereço atual recebe um aviso. */
public record TrocaDeEmailPedida(UUID id, String emailAtual, String nome, String emailNovo) {
}
