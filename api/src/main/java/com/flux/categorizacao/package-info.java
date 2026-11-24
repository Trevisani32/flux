/**
 * Regras de categorização (Fatia 04b, ADR 0015): o cadastro em ordem de prioridade, a sugestão na
 * revisão da importação, o "Aplicar regras" aos lançamentos sem categoria e o texto sugerido para
 * criar uma regra. Lê contas, cartões e categorias pelas consultas deles, implementa o {@code UsoDaCategoria}
 * (a regra é uso da categoria), a {@code LimpezaDaConta} e a {@code LimpezaDoCartao}, e
 * grava categorias em lançamentos pela {@code LancamentosParaRegras}. A API pública é só a
 * {@link com.flux.categorizacao.RegrasDeCategorizacao}, usada pela importação.
 */
package com.flux.categorizacao;
