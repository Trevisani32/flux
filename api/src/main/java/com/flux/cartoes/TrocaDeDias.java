package com.flux.cartoes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Decisão 7: mudar os dias de um cartão que já tem faturas. A fatura aberta e as fechadas ficam como
 * estão; os dias novos valem da seguinte em diante, e as futuras já gravadas são recalculadas, com as
 * compras delas trocando de fatura se for o caso. As faturas moram em lançamentos, que implementa.
 */
public interface TrocaDeDias {

    /**
     * O que a troca faria, sem gravar nada: a aberta, que fica, e a primeira fatura com os dias novos.
     * Vazio quando o cartão não tem lançamento nem ajuste de fechamento (os dias novos valem já).
     */
    Optional<Previa> previa(Long usuarioId, CartaoResumo cartao, int diaFechamento, int diaVencimento, LocalDate hoje);

    /**
     * Grava a aberta (se ainda não existe), recalcula as futuras e recoloca as compras. No cartão sem
     * lançamento nem ajuste de fechamento, apaga as faturas que restaram, sem nada dentro. Exige uma
     * transação aberta, com a trava de cartões. Recusa (422) se um mês passar do limite de lançamentos.
     */
    void aplicar(Long usuarioId, CartaoResumo cartao, int diaFechamento, int diaVencimento, LocalDate hoje);

    /** A aberta e a primeira fatura com os dias novos: o período, o fechamento e o vencimento dela. */
    record Previa(YearMonth abertaMes, LocalDate abertaFechamento, YearMonth primeiraMes, LocalDate primeiraInicio,
                  LocalDate primeiraFechamento, LocalDate primeiraVencimento) {
    }
}
