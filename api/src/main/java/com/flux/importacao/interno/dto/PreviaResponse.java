package com.flux.importacao.interno.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.flux.importacao.interno.leitura.FormatoCsv;

/**
 * Resposta de POST /api/importacoes/previa: as linhas do arquivo classificadas, na ordem em que a
 * revisão mostra (data mais recente primeiro; no mesmo dia, a linha de baixo do arquivo em cima).
 * Nada foi gravado. {@code hash} volta na confirmação, junto com o arquivo (decisão 13).
 * {@code perfil}: o perfil de CSV usado (nulo no OFX), para o resumo da decisão 12.
 * {@code origens}: as importações de onde vieram as linhas já importadas, a mais recente primeiro.
 */
public record PreviaResponse(
        String hash,
        String formato,
        String nomeArquivo,
        Long contaId,
        String contaNome,
        LocalDate dataSaldoInicial,
        LocalDate dataInicio,
        LocalDate dataFim,
        List<Linha> linhas,
        ArquivoRepetido arquivoRepetido,
        OutraConta outraConta,
        FormatoCsv perfil,
        List<Origem> origens) {

    /**
     * {@code situacao}: NOVA, PROVAVEL, JA_IMPORTADA, ANTES_DO_SALDO_INICIAL ou FORA; {@code motivo}
     * só em FORA: ILEGIVEL, VALOR_ZERO, LINHA_DE_SALDO ou DATA_ALEM_DO_LIMITE. Na ilegível, data,
     * descrição e valor vêm nulos, e {@code erro} diz o que faltou. {@code parecido}: na provável,
     * com quem ela se parece; na já importada, o lançamento dela. {@code sugestao}: só na linha para
     * importar, a primeira regra que casa (Fatia 04b); nula sem regra.
     */
    public record Linha(int linha, LocalDate data, String descricao, BigDecimal valor, String situacao, String motivo,
                        boolean marcada, boolean futuro, String erro, Parecido parecido, Sugestao sugestao) {
    }

    /**
     * A regra que categorizou a linha na revisão (decisão 14 da 04b): a posição e a condição para a tela
     * dizer "pela 2ª regra", a categoria e a descrição legível (nula = fica a do extrato).
     */
    public record Sugestao(Long regraId, int posicao, String condicao, String texto, Long categoriaId, String descricao) {
    }

    /**
     * {@code criadoEm}: quando o existente foi lançado à mão ou importado; {@code excluidoEm}: quando
     * foi excluído (só no ignorado). A frase da revisão usa essas datas (decisão 17).
     */
    public record Parecido(String descricao, LocalDate data, boolean manual, boolean excluido, Instant criadoEm,
                           Instant excluidoEm) {
    }

    /** Uma importação de onde vieram linhas já importadas ("Vieram de extrato-outubro.ofx, importado em 04/11/2026"). */
    public record Origem(String nomeArquivo, Instant importadaEm) {
    }

    /** O mesmo arquivo já foi importado (decisão 25). */
    public record ArquivoRepetido(Instant importadoEm, Long contaId, String contaNome, boolean mesmaConta) {
    }

    /** O mesmo extrato (BANKID e ACCTID do OFX) já foi importado em outra conta (decisão 11). */
    public record OutraConta(Long contaId, String contaNome, String finalDaConta) {
    }
}
