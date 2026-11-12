/**
 * Lançamentos e transferências (Fatia 03b), os lançamentos importados (Fatia 04a) e a conversão de
 * importados em transferência (Fatia 04c, ADR 0016). Implementa as interfaces que contas e categorias
 * declaram (MovimentoDasContas, UsoDaConta, LimpezaDaConta e UsoDaCategoria) e lê contas e categorias
 * pelas consultas deles (ADR 0012 e ADR 0013). A API pública é a
 * {@link com.flux.lancamentos.LancamentosImportados}, usada pela importação (ADR 0014; desde a 04c,
 * também os pares sugeridos e os importados convertidos), e a
 * {@link com.flux.lancamentos.LancamentosParaRegras}, usada pelas regras de categorização (ADR 0015).
 */
package com.flux.lancamentos;