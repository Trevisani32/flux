package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.flux.lancamentos.interno.OrigemLancamento;

/**
 * Resposta de GET /api/lancamentos/{id}/pares (Fatia 04c, decisão 27): o importado que vai virar
 * transferência, a janela de datas em que o par é procurado ({@code inicio} e {@code fim}, 5 dias para
 * cada lado) e os pares possíveis, o mais perto da data primeiro (no máximo 20).
 */
public record ParesResponse(ItemDaLista lancamento, LocalDate inicio, LocalDate fim, List<Par> pares) {

    /** Um par possível: {@code valor} com sinal, oposto ao do lançamento; {@code origem} MANUAL ou IMPORTADO. */
    public record Par(Long id, LocalDate data, String descricao, BigDecimal valor, Long contaId, String contaNome,
                      OrigemLancamento origem) {
    }
}