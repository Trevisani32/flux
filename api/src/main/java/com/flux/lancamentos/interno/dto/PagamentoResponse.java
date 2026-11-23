package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.lancamentos.interno.OrigemLancamento;

/**
 * Um pagamento de fatura (Fatia 05b, decisões 30 a 33 e 37), resposta de criar, buscar, editar e converter.
 *
 * <ul>
 *   <li>{@code faturaMes} é "2026-11"; {@code faturaVencimento}, o vencimento dela;</li>
 *   <li>{@code valor} é positivo (na conta, o lançamento é negativo);</li>
 *   <li>{@code origem}: MANUAL (lançado à mão, se exclui) ou IMPORTADO (a despesa do extrato convertida: conta, data
 *       e valor não mudam, e ela se desfaz); {@code descricaoBanco} só no importado;</li>
 *   <li>{@code somenteLeitura}: a conta ou o cartão está arquivado (decisão 33).</li>
 * </ul>
 */
public record PagamentoResponse(
        Long id,
        Long cartaoId,
        String cartaoNome,
        boolean cartaoArquivado,
        String faturaMes,
        LocalDate faturaVencimento,
        Long contaId,
        String contaNome,
        boolean contaArquivada,
        LocalDate data,
        BigDecimal valor,
        String observacao,
        OrigemLancamento origem,
        String descricaoBanco,
        boolean somenteLeitura) {
}
