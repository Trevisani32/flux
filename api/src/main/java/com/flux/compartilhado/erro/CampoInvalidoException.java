package com.flux.compartilhado.erro;

/**
 * Campo inválido por uma regra que a Bean Validation não alcança (por exemplo, uma data
 * comparada com o Clock da aplicação). Vira o mesmo 400 da validação, com o campo em "campos".
 */
public class CampoInvalidoException extends RuntimeException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
