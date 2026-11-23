package com.flux.lancamentos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * O que a importação de extratos (Fatia 04a) pede aos lançamentos: os que já existem na conta,
 * para classificar as linhas do arquivo, e gravar, desfazer e contar os importados (ADR 0006 e
 * ADR 0014). Desde a Fatia 04c, também os pares de transferência que o resultado sugere e os
 * importados convertidos em transferência (ADR 0016); desde a Fatia 05b, os que viraram pagamento de fatura (ADR 0018).
 * Só lançamentos grava na tabela de lançamentos.
 */
public interface LancamentosImportados {

    /**
     * Os lançamentos da conta com data entre {@code inicio} e {@code fim}, mais os que têm uma
     * das {@code chaves}, de qualquer data. Inclui os ignorados e os lados de transferência.
     */
    List<Existente> existentes(Long usuarioId, Long contaId, LocalDate inicio, LocalDate fim, Collection<String> chaves);

    /**
     * Grava as linhas escolhidas como lançamentos importados da importação. Exige uma transação
     * aberta; pega as travas de contas e de categorias, confere os limites (decisão 24) e a
     * categoria de cada linha.
     */
    void gravar(Long usuarioId, Long contaId, Long importacaoId, List<Novo> novos);

    /**
     * Apaga todos os lançamentos da importação, inclusive os editados e os ignorados. Antes, desfaz a
     * conversão dos que estão numa transferência (Fatia 04c, decisão 34): o outro lado volta a ser
     * receita ou despesa, sem categoria, e o 422 recusa tudo se ele está numa conta arquivada. Desde a Fatia
     * 05b, a despesa que virou pagamento de fatura sai junto, e a fatura volta a ter o pendente; o 422 recusa
     * tudo se o cartão dela está arquivado. Exige transação.
     */
    int desfazer(Long usuarioId, Long importacaoId);

    /** Ignorados e editados de cada importação do usuário (só as que ainda têm lançamentos). */
    List<Contagem> contagens(Long usuarioId);

    /** Os ignorados da importação, o último excluído primeiro. */
    List<Excluido> excluidos(Long usuarioId, Long importacaoId);

    /**
     * Fatia 04c (decisão 35): as linhas da importação com um só par possível (decisão 27), para o
     * resultado sugerir. Só lê: nada se converte sozinho.
     */
    List<ParSugerido> paresSugeridos(Long usuarioId, Long importacaoId);

    /** Fatia 04c (decisão 34): os importados do usuário que estão numa transferência, de todas as importações. */
    List<Convertido> convertidos(Long usuarioId);

    /**
     * Fatia 05b: os importados do usuário que viraram pagamento de fatura, de todas as importações, o mais antigo
     * primeiro, para o histórico avisar antes do desfazer, que os apaga (como as transferências da 04c).
     */
    List<PagamentoConvertido> pagamentosConvertidos(Long usuarioId);

    /**
     * Um lançamento que já existe. {@code importacaoId} e {@code chaveImportacao} só no importado;
     * {@code criadoEm} é quando ele foi lançado à mão ou importado, e {@code ignoradoEm}, quando foi
     * excluído (decisão 17: a frase do parecido diz essas datas).
     */
    record Existente(Long id, LocalDate data, BigDecimal valor, String descricao, String descricaoBanco,
                     String chaveImportacao, Long importacaoId, Instant criadoEm, Instant ignoradoEm) {

        /** Lançado à mão (origem MANUAL): o banco garante que só o importado tem importação. */
        public boolean manual() {
            return importacaoId == null;
        }

        public boolean ignorado() {
            return ignoradoEm != null;
        }
    }

    /**
     * Uma linha do arquivo para gravar; o sinal do valor dá o tipo. {@code linha} vai no 422 da
     * categoria. {@code descricao} é a que a revisão mostrou (a legível da regra, Fatia 04b, ou a do
     * extrato); {@code descricaoBanco}, a do extrato.
     */
    record Novo(int linha, LocalDate data, BigDecimal valor, String descricao, String descricaoBanco, String chave,
                Long categoriaId) {
    }

    record Contagem(Long importacaoId, long editados, long excluidos) {
    }

    record Excluido(Long id, LocalDate data, String descricao, BigDecimal valor, Instant ignoradoEm) {
    }

    /** Uma linha da importação e o único par dela. */
    record ParSugerido(LadoSugerido lancamento, LadoSugerido par) {
    }

    /** Um lado de um par sugerido; {@code valor} com sinal. */
    record LadoSugerido(Long id, LocalDate data, String descricao, BigDecimal valor, Long contaId, String contaNome) {
    }

    /**
     * Um importado numa transferência: a importação, a data dele, a conta do outro lado e o tipo que o
     * outro lado volta a ter se a conversão for desfeita (RECEITA ou DESPESA).
     */
    record Convertido(Long importacaoId, LocalDate data, Long outraContaId, String tipoDoOutroLado) {
    }

    /**
     * Um importado que virou pagamento de fatura: a importação, a data dele, o cartão (com o nome e se está arquivado:
     * arquivado, o desfazer é recusado) e o mês da fatura paga ("2026-11").
     */
    record PagamentoConvertido(Long importacaoId, LocalDate data, Long cartaoId, String cartaoNome, boolean cartaoArquivado,
                               String faturaMes) {
    }
}
