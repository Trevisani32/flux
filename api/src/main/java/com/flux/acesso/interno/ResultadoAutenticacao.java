package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.SessaoResponse;

public record ResultadoAutenticacao(SessaoResponse sessao, String tokenRenovacao) {
}
