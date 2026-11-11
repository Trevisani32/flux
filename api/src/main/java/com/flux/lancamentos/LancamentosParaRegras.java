package com.flux.lancamentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O que as regras de categorização (Fatia 04b) pedem aos lançamentos: as receitas e despesas para
 * casar com as regras, e gravar a categoria nos que estão sem (ADR 0015). Transferências e excluídos
 * (ignorados) nunca entram.
 */
public interface LancamentosParaRegras {

    /** As receitas e despesas não excluídas do usuário, com e sem categoria, de todas as contas. */
    List<Candidato> receitasEDespesas(Long usuarioId);

    /**
     * Põe a categoria em cada lançamento que ainda está sem (decisão 18: nunca troca uma categoria) e,
     * no importado cuja descrição ainda é a do extrato, a descrição legível, se vier. Exige uma
     * transação aberta; pega as travas de contas e de categorias. Pula o que não serve mais (já tem
     * categoria, foi excluído, virou transferência, está em conta arquivada ou a categoria não é da
     * natureza). Devolve quantos mudaram.
     */
    int categorizar(Long usuarioId, List<Categorizacao> categorizacoes);

    /**
     * Um lançamento para as regras. {@code descricaoBanco} só no importado; {@code categoriaId} nulo =
     * sem categoria.
     */
    record Candidato(Long id, Long contaId, LocalDate data, BigDecimal valor, String descricao, String descricaoBanco,
                     Long categoriaId) {

        public boolean importado() {
            return descricaoBanco != null;
        }

        /** O texto que as regras olham (decisão 3): o do extrato no importado, a descrição no manual. */
        public String textoParaRegras() {
            return importado() ? descricaoBanco : descricao;
        }
    }

    /** {@code descricao} nula = a regra não troca a descrição. */
    record Categorizacao(Long lancamentoId, Long categoriaId, String descricao) {
    }
}
