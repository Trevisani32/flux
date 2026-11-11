package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.lancamentos.interno.OrigemLancamento;
import com.flux.lancamentos.interno.TipoLancamento;

/**
 * Uma linha da lista: uma receita, uma despesa ou uma transferência inteira (os dois lados
 * numa linha só, decisão 26). É também a resposta de buscar, criar e editar.
 *
 * <ul>
 *   <li>{@code id}: o do lançamento, ou o da transferência;</li>
 *   <li>{@code valor}: com sinal na receita e na despesa; na transferência, o valor movido,
 *       positivo, que a tela mostra sem sinal;</li>
 *   <li>{@code contaId}/{@code contaNome}: a conta do lançamento, ou a de origem da
 *       transferência; {@code contaDestinoId}/{@code contaDestinoNome} só na transferência;</li>
 *   <li>{@code direcao}: só na transferência e com o filtro de uma das contas dela:
 *       "SAIDA" (a conta filtrada é a origem) ou "ENTRADA";</li>
 *   <li>{@code futuro}: data depois de hoje (ainda fora do saldo);</li>
 *   <li>{@code somenteLeitura}: alguma conta do item está arquivada (decisões 23 e 28);</li>
 *   <li>{@code origem}: MANUAL ou IMPORTADO; {@code descricaoBanco} só no importado, a descrição
 *       que veio do extrato (decisões 28 e 29).</li>
 * </ul>
 */
public record ItemDaLista(
        TipoLancamento tipo,
        Long id,
        LocalDate data,
        String descricao,
        BigDecimal valor,
        String observacao,
        Long contaId,
        String contaNome,
        Long categoriaId,
        String categoriaNome,
        String categoriaMaeNome,
        boolean categoriaArquivada,
        Long contaDestinoId,
        String contaDestinoNome,
        String direcao,
        boolean futuro,
        boolean somenteLeitura,
        OrigemLancamento origem,
        String descricaoBanco) {
}
