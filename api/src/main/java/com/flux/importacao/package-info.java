/**
 * Importação de extratos OFX e CSV (Fatia 04a): revisão, confirmação, histórico, desfazer e o
 * perfil de CSV de cada conta (ADR 0006 e ADR 0014). Não tem API pública: lê contas pela
 * consulta delas, pede às regras de categorização a sugestão de cada linha na revisão
 * ({@code RegrasDeCategorizacao}, Fatia 04b) e grava os lançamentos pela {@code LancamentosImportados}.
 */
package com.flux.importacao;
