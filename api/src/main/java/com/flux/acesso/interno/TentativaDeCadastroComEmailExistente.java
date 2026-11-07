package com.flux.acesso.interno;

import java.util.UUID;

/** Alguém tentou criar conta com o e-mail de uma conta confirmada. O id deixa cada evento único. */
public record TentativaDeCadastroComEmailExistente(UUID id, Long usuarioId) {
}
