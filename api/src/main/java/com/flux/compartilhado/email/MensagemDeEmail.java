package com.flux.compartilhado.email;

/** Um e-mail pronto: destinatário, assunto e as versões HTML e texto simples. */
public record MensagemDeEmail(String para, String assunto, String html, String texto) {
}
