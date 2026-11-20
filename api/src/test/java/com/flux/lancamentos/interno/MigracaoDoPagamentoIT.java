package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A V9 (decisões 32, 36 e 46 da Fatia 05): o banco recusa o pagamento que o serviço também recusa. */
class MigracaoDoPagamentoIT extends PagamentosBase {

    @Test
    void oBancoGuardaOPagamentoNaContaNegativoSemCategoriaENaFaturaDoUsuario() {
        assertThat(jdbc.queryForList("select version || ':' || success from flyway_schema_history order by installed_rank",
                String.class)).contains("9:true");
        assertThat(jdbc.queryForList("select indexdef from pg_indexes where indexname = 'ix_lancamentos_fatura_paga'", String.class))
                .containsExactly("CREATE INDEX ix_lancamentos_fatura_paga ON public.lancamentos USING btree (fatura_paga_id) "
                        + "WHERE (fatura_paga_id IS NOT NULL)");
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long compra = novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        long pagamento = pagarHoje(usuario, cartao, mesAtras(2), conta, "100");
        long faturaPaga = jdbc.queryForObject("select fatura_paga_id from lancamentos where id = ?", Long.class, pagamento);
        long parcela = jdbc.queryForObject("select id from lancamentos where compra_id = ?", Long.class, compra);
        long cartaoDoOutro = cartaoQueFecha5(outro, "Cartão do outro", novaConta(outro, "Conta do outro"));
        novaCompra(outro, cartaoDoOutro, diaUmAtras(2), "Do outro", "10", 1);
        long faturaDoOutro = jdbc.queryForObject("select id from faturas where cartao_id = ?", Long.class, cartaoDoOutro);

        assertThatThrownBy(() -> jdbc.update("update lancamentos set valor = 100 where id = ?", pagamento))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_sinal");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set fatura_paga_id = null where id = ?", pagamento))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_pagamento");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set tipo = 'DESPESA' where id = ?", pagamento))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_pagamento");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set categoria_id = ? where id = ?", idDaCategoria(usuario, "Compras"),
                pagamento)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_lancamentos_categoria");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set fatura_paga_id = ? where id = ?", faturaDoOutro, pagamento))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_lancamentos_fatura_paga");
        // O importado convertido também não fica excluído (ignorado): é o CHECK da V9, e não o da V5, que segura.
        long convertido = importado(usuario, conta, hoje().toString(), "-50", "PAG FATURA");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartao + ",\"mes\":\""
                + mesAtras(2) + "\"}", convertido)).hasStatus(201);
        assertThatThrownBy(() -> jdbc.update("update lancamentos set ignorado_em = now() where id = ?", convertido))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos_pagamento_nao_ignorado");
        assertThatThrownBy(() -> jdbc.update("update lancamentos set tipo = 'PAGAMENTO_FATURA', fatura_paga_id = ? where id = ?", faturaPaga,
                parcela)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_lancamentos");
        assertThatThrownBy(() -> jdbc.update("delete from faturas where id = ?", faturaPaga))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_lancamentos");
        assertThat(jdbc.update("update faturas set paga_fora_em = now() where id = ?", faturaPaga)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where tipo = 'PAGAMENTO_FATURA' and usuario_id = ?", Integer.class,
                usuario.id())).isEqualTo(2);
    }
}
