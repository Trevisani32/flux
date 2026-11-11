/**
 * Lançamentos e transferências (Fatia 03b) e os lançamentos importados (Fatia 04a). Implementa
 * as interfaces que contas e categorias declaram (MovimentoDasContas, UsoDaConta, LimpezaDaConta
 * e UsoDaCategoria) e lê contas e categorias pelas consultas deles (ADR 0012 e ADR 0013). A API
 * pública é só a {@link com.flux.lancamentos.LancamentosImportados}, usada pela importação
 * (ADR 0014).
 */
package com.flux.lancamentos;
