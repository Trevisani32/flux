/**
 * Importação de extratos OFX e CSV (Fatia 04a): revisão, confirmação, histórico, desfazer e o
 * perfil de CSV de cada conta (ADR 0006 e ADR 0014). Não tem API pública: lê contas pela
 * consulta delas e grava os lançamentos pela {@code LancamentosImportados}.
 */
package com.flux.importacao;
