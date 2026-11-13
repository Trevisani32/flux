package com.flux.lancamentos.interno;

import com.flux.lancamentos.LancamentosParaRegras;
import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 22 a 27 da 05a: as parcelas e os estornos na lista do mês, no dia do vencimento; o filtro de cartão;
 * os últimos, o saldo, /api/lancamentos/{id}, as regras e a conversão só com lançamentos de conta.
 */
class ListaComCartoesIT extends CartoesBase {

    @Autowired
    private LancamentosParaRegras paraRegras;

    @Test
    void aListaTemAsParcelasNoVencimentoEOFiltroDeCartaoMostraAFatura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long compras = idDaCategoria(usuario, "Compras");
        api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-05", "Sapataria", "450", 3, 1, compras)));
        api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-11", "Estorno Sapataria", "50", null)));
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "18", "2025-12-12", "Padaria", conta, null)));

        MvcTestResult dezembro = api.get(usuario, "/api/lancamentos?mes=2025-12");
        assertThat(dezembro).hasStatus(200).bodyJson().extractingPath("dias[*].data").asArray().containsExactly("2025-12-12", "2025-12-10");
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[*].descricao").asArray()
                .containsExactly("Estorno Sapataria", "Sapataria");
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[1].cartaoNome").isEqualTo("Cartão Horizonte");
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[1].contaId").isNull();
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[1].parcela").isEqualTo(1);
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[1].parcelas").isEqualTo(3);
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[1].dataCompra").isEqualTo("2025-11-05");
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[1].categoriaNome").isEqualTo("Compras");
        assertThat(dezembro).bodyJson().extractingPath("dias[1].itens[0].estorno").isEqualTo(true);
        assertThat(dezembro).bodyJson().extractingPath("fatura").isNull();
        assertThat(texto(dezembro)).contains("\"entradas\":50.00").contains("\"saidas\":-168.00");

        MvcTestResult doCartao = api.get(usuario, "/api/lancamentos?mes=2025-12&cartao=" + horizonte);
        assertThat(doCartao).bodyJson().extractingPath("dias[*].itens[*].descricao").asArray().containsExactly("Estorno Sapataria", "Sapataria");
        assertThat(doCartao).bodyJson().extractingPath("dias[0].saldoNoFimDoDia").isNull();
        assertThat(doCartao).bodyJson().extractingPath("fatura.mes").isEqualTo("2025-12");
        assertThat(doCartao).bodyJson().extractingPath("fatura.fechamento").isEqualTo("2025-12-03");
        assertThat(doCartao).bodyJson().extractingPath("fatura.situacao").isEqualTo("FECHADA");
        assertThat(texto(doCartao)).contains("\"total\":100.00");
        MvcTestResult daConta = api.get(usuario, "/api/lancamentos?mes=2025-12&conta=" + conta);
        assertThat(daConta).bodyJson().extractingPath("dias[*].itens[*].descricao").asArray().containsExactly("Padaria");
        assertCampo(api.get(usuario, "/api/lancamentos?mes=2025-12&conta=" + conta + "&cartao=" + horizonte), "cartao",
                "Filtre por uma conta ou por um cartão, não pelos dois.");

        assertThat(api.get(usuario, "/api/lancamentos/ultimos")).bodyJson().extractingPath("[*].descricao").asArray().containsExactly("Padaria");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", conta))).contains("\"saldoAtual\":-18.00");
        long parcela = jdbc.queryForObject("select id from lancamentos where descricao = 'Sapataria' and usuario_id = ? and numero_parcela = 1",
                Long.class, usuario.id());
        assertThat(api.get(usuario, "/api/lancamentos/{id}", parcela)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Lançamento não encontrado.");
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "1", "2025-12-10", "X", conta, null), parcela)).hasStatus(404);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", parcela)).hasStatus(404);
    }

    /** Decisão 27: as regras não olham lançamentos de fatura, e a conversão só vê contas. */
    @Test
    void regrasEConversaoIgnoramOsLancamentosDeFatura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long outra = novaConta(usuario, "Conta conjunta");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long compras = idDaCategoria(usuario, "Compras");
        api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-05", "Mercado do cartao", "33.34", 1, 1, null)));
        assertThat(paraRegras.receitasEDespesas(usuario.id())).isEmpty();
        assertThat(api.post(usuario, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"mercado do cartao\",\"contaId\":null,"
                + "\"categoriaId\":" + compras + "}")).hasStatus(200).bodyJson().extractingPath("casam").isEqualTo(0);

        Long importacao = jdbc.queryForObject("insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, "
                + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (?, ?, 'OFX', 'teste.ofx', "
                + "md5(random()::text) || md5(random()::text), date '2025-12-11', date '2025-12-11', 1, 1, 0, 0, 0, 0) returning id",
                Long.class, usuario.id(), outra);
        Long importado = jdbc.queryForObject("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, "
                + "origem, importacao_id, chave_importacao, descricao_banco) values (?, ?, date '2025-12-11', 'ESTORNO', 'estorno', 33.34, "
                + "'RECEITA', 'IMPORTADO', ?, 'ID:x', 'ESTORNO') returning id", Long.class, usuario.id(), outra, importacao);
        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", importado)).hasStatus(200).bodyJson().extractingPath("pares").asArray().isEmpty();
        long parcela = jdbc.queryForObject("select id from lancamentos where usuario_id = ? and compra_id is not null", Long.class, usuario.id());
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter", "{\"parId\":" + parcela + ",\"contaId\":null,\"descricao\":null}",
                importado)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("PAR_INVALIDO");
        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", parcela)).hasStatus(404);
    }
}
