/**
 * Exportar meus dados (Fatia 06b, ADR 0021): só leitura. Lê as tabelas dos outros módulos por SQL, sempre filtrando pelo
 * usuário (ADR 0001), e usa do acesso só a conferência da senha atual; ninguém depende dele.
 */
package com.flux.exportacao;
