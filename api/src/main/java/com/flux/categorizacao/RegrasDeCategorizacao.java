package com.flux.categorizacao;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que a importação pede às regras (decisão 14): para cada linha do arquivo, a primeira regra ativa
 * que casa com a descrição do extrato, na conta e na natureza do sinal. Uma regra pausada (categoria
 * ou conta arquivada) não casa.
 */
public interface RegrasDeCategorizacao {

    /**
     * Uma sugestão por alvo, na mesma ordem; nula quando nenhuma regra casa. Só lê: não grava nada.
     */
    List<Sugestao> sugerir(Long usuarioId, Long contaId, List<Alvo> alvos);

    /** Uma linha do arquivo: a descrição do extrato e o valor com sinal (receita positiva, despesa negativa). */
    record Alvo(String descricao, BigDecimal valor) {
    }

    /**
     * A regra que casou: a posição e a condição (para a revisão dizer "pela 2ª regra"), a categoria e a
     * descrição legível (nula quando a regra não troca a descrição).
     */
    record Sugestao(Long regraId, int posicao, String condicao, String texto, Long categoriaId, String descricao) {
    }
}
