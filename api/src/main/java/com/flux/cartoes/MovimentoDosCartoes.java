package com.flux.cartoes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * O que os lançamentos sabem das faturas de cada cartão, para a tela de Cartões e o Início (decisões 26 e
 * 29; na Fatia 05b, o pendente do limite disponível, decisão 38). O módulo de cartões declara e o de lançamentos
 * implementa, como o {@code MovimentoDasContas}. Sem implementação, a fatura aberta vem das datas padrão, zerada.
 */
public interface MovimentoDosCartoes {

    /** Por id de cartão: a fatura aberta em {@code hoje}, a seguinte, o total das abertas e futuras e o pendente. */
    Map<Long, Movimento> movimento(Long usuarioId, List<CartaoResumo> cartoes, LocalDate hoje);

    /** Uma fatura, gravada ou não. {@code total} = compras − estornos (negativo é crédito), escala 2. */
    record FaturaResumida(YearMonth mes, LocalDate inicio, LocalDate fechamento, LocalDate vencimento, BigDecimal total, long lancamentos) {
    }

    /**
     * {@code emAberto} soma os totais da aberta e das futuras, e {@code lancamentosEmAberto} conta os
     * lançamentos delas (decisão 5: a confirmação de arquivar diz quanto). {@code pendente} (Fatia 05b, decisão 38)
     * soma o que falta pagar em todas as faturas, inclusive as futuras, cada uma nunca negativa: o limite disponível
     * é o limite menos ele. Escala 2.
     */
    record Movimento(FaturaResumida aberta, FaturaResumida proxima, BigDecimal emAberto, long lancamentosEmAberto, BigDecimal pendente) {
    }

    /**
     * Fatia 05b (decisão 38): a parte do limite tomada pelo pendente, de 0 a 100, arredondada; acima do limite, 100. É a
     * largura da barra do limite em Cartões e na fatura: as duas telas usam esta conta, e o web não faz conta com dinheiro.
     */
    static int usoDoLimite(BigDecimal limite, BigDecimal pendente) {
        if (pendente.compareTo(limite) >= 0) {
            return 100;
        }
        return pendente.multiply(BigDecimal.valueOf(100)).divide(limite, 0, RoundingMode.HALF_UP).intValueExact();
    }
}
