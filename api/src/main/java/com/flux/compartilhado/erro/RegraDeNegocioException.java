package com.flux.compartilhado.erro;

/**
 * Regra de negócio violada: vira 422. O {@code codigo}, quando existe, vai no corpo
 * (propriedade "codigo" do ProblemDetail) para a tela decidir sem depender do texto.
 */
public class RegraDeNegocioException extends RuntimeException {

    private final String codigo;

    public RegraDeNegocioException(String mensagem) {
        this(mensagem, null);
    }

    public RegraDeNegocioException(String mensagem, String codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
