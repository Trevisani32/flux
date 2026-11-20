/**
 * Lançamentos e transferências (Fatia 03b), os lançamentos importados (Fatia 04a), a conversão de
 * importados em transferência (Fatia 04c, ADR 0016) e, desde a Fatia 05a, as faturas, as compras e os
 * estornos no cartão (ADR 0017). Implementa as interfaces que contas, categorias e cartões declaram
 * (MovimentoDasContas, UsoDaConta, LimpezaDaConta, UsoDaCategoria, MovimentoDosCartoes, UsoDoCartao e
 * TrocaDeDias) e lê contas, categorias e cartões pelas consultas deles (ADR 0012, ADR 0013 e ADR 0017). A API pública é a
 * {@link com.flux.lancamentos.LancamentosImportados}, usada pela importação (ADR 0014; desde a 04c,
 * também os pares sugeridos e os importados convertidos), e a
 * {@link com.flux.lancamentos.LancamentosParaRegras}, usada pelas regras de categorização (ADR 0015).
 */
package com.flux.lancamentos;
