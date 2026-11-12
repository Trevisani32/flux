package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A V7 (decisão 33 da 04c): um lado de transferência nunca fica excluído, nem por um UPDATE direto. */
class MigracaoDaConversaoIT extends ConversaoBase {

    @Test
    void oBancoRecusaIgnorarUmLadoDeTransferencia() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("7:true");
        assertThat(jdbc.queryForObject("select pg_get_constraintdef(oid) from pg_constraint where conname = 'ck_lancamentos_lado_nao_ignorado'",
                String.class)).isEqualTo("CHECK (((transferencia_id IS NULL) OR (ignorado_em IS NULL)))");
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED");
        long recebida = importado(usuario, conjunta, "2026-11-02", "1000.00", "RECEBIDA");
        converterComPar(usuario, ted, recebida, null);

        assertThatThrownBy(() -> jdbc.update("update lancamentos set ignorado_em = now() where id = ?", ted))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_lado_nao_ignorado");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where id in (?, ?) and ignorado_em is null", Integer.class,
                ted, recebida)).isEqualTo(2);
    }
}