package com.flux.acesso.interno;

import java.time.Instant;

public record TokenAcessoEmitido(String valor, Instant expiraEm) {
}
