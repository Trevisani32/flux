package com.flux.lancamentos.interno;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A V8 (decisões 46 e 47 da 05a): o banco recusa o que o serviço também recusa, e o vencimento leva as parcelas. */
class MigracaoDosCartoesIT extends CartoesBase {

    @Test
    void oBancoGuardaContaOuFaturaAParcelaNoVencimentoEOsLimitesDaCompra() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("8:true");
        // A lista dos cartões lê as faturas pelo usuário.
        assertThat(jdbc.queryForList("select indexdef from pg_indexes where indexname = 'ix_faturas_usuario'", String.class))
                .containsExactly("CREATE INDEX ix_faturas_usuario ON public.faturas USING btree (usuario_id)");
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long compra = novaCompra(usuario, horizonte, "2025-11-09", "Loja", "300", 3);
        long parcela = jdbc.queryForObject("select id from lancamentos where compra_id = ? and numero_parcela = 1", Long.class, compra);

        assertThatThrownBy(() -> jdbc.update("update lancamentos set conta_id = ? where id = ?", conta, parcela))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_conta_ou_fatura");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set data = data + 1 where id = ?", parcela))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_lancamentos_fatura");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set valor = 100 where id = ?", parcela))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_sinal");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set numero_parcela = 2 where id = ?", parcela))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ux_lancamentos_parcela");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set importacao_id = 1, chave_importacao = 'x', descricao_banco = 'x', "
                + "origem = 'IMPORTADO' where id = ?", parcela))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_importado_em_conta");
        assertThatThrownBy(() -> jdbc.update("update compras set valor_total = 0.02 where id = ?", compra))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_compras_valor");
        assertThatThrownBy(() -> jdbc.update("update cartoes set dia_fechamento = 28, dia_vencimento = 30 where id = ?", horizonte))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_cartoes_dias");
        assertThatThrownBy(() -> jdbc.update("delete from compras where id = ?", compra))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_lancamentos_compra");

        // Decisão 47: mudar o vencimento da fatura leva a data das parcelas dela junto (ON UPDATE CASCADE).
        jdbc.update("update faturas set data_vencimento = date '2025-12-11' where cartao_id = ? and mes_referencia = date '2025-12-01'",
                horizonte);
        assertThat(parcelas(compra).getFirst()).isEqualTo("1 2025-12-11 -100.00 2025-12");
    }
}
