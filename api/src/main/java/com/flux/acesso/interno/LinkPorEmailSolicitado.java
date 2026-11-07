package com.flux.acesso.interno;

/** Um link por e-mail foi criado e precisa ser enviado (depois do commit). Leva só o id do token. */
public record LinkPorEmailSolicitado(Long tokenId) {
}
