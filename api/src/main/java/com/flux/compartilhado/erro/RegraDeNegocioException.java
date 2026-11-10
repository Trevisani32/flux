package com.flux.compartilhado.erro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regra de negócio violada: vira 422. O {@code codigo}, quando existe, vai no corpo
 * (propriedade "codigo" do ProblemDetail) para a tela decidir sem depender do texto. As
 * propriedades acrescentadas com {@link #com(String, Object)} também vão no corpo, com o
 * nome dado (ex.: "uso" na exclusão de uma conta usada).
 */
public class RegraDeNegocioException extends RuntimeException {

    private final String codigo;
    private final Map<String, Object> propriedades = new LinkedHashMap<>();

    public RegraDeNegocioException(String mensagem) {
        this(mensagem, null);
    }

    public RegraDeNegocioException(String mensagem, String codigo) {
        super(mensagem);
        this.codigo = codigo;
    }

    /** Acrescenta uma propriedade ao corpo do 422 e devolve a própria exceção. */
    public RegraDeNegocioException com(String nome, Object valor) {
        propriedades.put(nome, valor);
        return this;
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, Object> getPropriedades() {
        return Collections.unmodifiableMap(propriedades);
    }
}
