package com.flux.compartilhado.email;

/** Envio de e-mail. Lança exceção quando o servidor não aceita a mensagem. */
public interface EnvioDeEmail {

    void enviar(MensagemDeEmail mensagem);
}
