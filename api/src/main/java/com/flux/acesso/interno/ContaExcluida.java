package com.flux.acesso.interno;

import java.time.Instant;
import java.util.UUID;

/** A conta foi excluída. Os dados do e-mail de confirmação são montados antes de apagar. */
public record ContaExcluida(UUID id, String email, String nome, Instant excluidaEm) {
}
