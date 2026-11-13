/**
 * Cartões de crédito (Fatia 05a, ADR 0017): o cadastro, os dias de fechamento e de vencimento, o limite e
 * a conta de pagamento, e a classe pura de datas ({@link com.flux.cartoes.CalendarioDoCartao}). As
 * faturas, as compras e as parcelas moram em lançamentos, que lê os cartões pela
 * {@link com.flux.cartoes.CartoesConsulta} e implementa as interfaces que este módulo declara
 * ({@link com.flux.cartoes.MovimentoDosCartoes}, {@link com.flux.cartoes.UsoDoCartao} e
 * {@link com.flux.cartoes.TrocaDeDias}). A conta de pagamento é uso da conta: o módulo implementa o
 * {@code UsoDaConta} de contas.
 */
package com.flux.cartoes;
