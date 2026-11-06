package com.flux.acesso.interno.dto;

import java.time.Instant;

public record SessaoResponse(String tokenAcesso, Instant expiraEm, UsuarioResponse usuario) {
}
