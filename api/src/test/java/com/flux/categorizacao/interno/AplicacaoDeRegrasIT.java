package com.flux.categorizacao.interno;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.flux.lancamentos.LancamentosParaRegras;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Aplicar regras" (decisões 16 a 18 da 04b): só receitas e despesas sem categoria de contas ativas,
 * pela primeira regra que casa; a legível só no importado ainda com a descrição do extrato; nada que já
 * tem categoria muda. Regra que não é do usuário dá 422, e a gravação vai em lotes de 1.000.
 */
class AplicacaoDeRegrasIT extends RegrasBase {

    @Autowired
    private LancamentosParaRegras lancamentosParaRegras;

    @Autowired
    private TransactionTemplate transacao;

    @Test
    void previaAgrupaPelaPrimeiraRegraEAplicaSemTrocarONadaJaEscolhido() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long delivery = idDaCategoria(usuario, "Delivery");
        long app = idDaCategoria(usuario, "App de transporte");
        long lazer = idDaCategoria(usuario, "Lazer");
        long regraDoUber = criarRegra(usuario, "CONTEM", "uber", null, app, "Uber");
        long regraDoEats = criarRegra(usuario, "CONTEM", "uber eats", null, delivery, null);
        long eats = importado(usuario, horizonte, "2026-11-08", "-52.00", "UBER EATS*PEDIDO 991");
        long trip = importado(usuario, horizonte, "2026-11-09", "-23.90", "PAG*UBERTRIP 8812");
        long editado = importado(usuario, horizonte, "2026-11-07", "-31.70", "UBER *TRIP HELP.UBER.COM");
        jdbc.update("update lancamentos set descricao = 'Corrida do aeroporto', descricao_chave = 'corrida do aeroporto' where id = ?", editado);
        long aMao = manual(usuario, horizonte, "DESPESA", "10.00", "Uber", null);
        long escolhido = importado(usuario, horizonte, "2026-11-06", "-18.40", "UBER* TRIP 5521");
        jdbc.update("update lancamentos set categoria_id = ? where id = ?", lazer, escolhido);
        long estorno = importado(usuario, horizonte, "2026-11-06", "18.40", "ESTORNO UBER *TRIP");
        long ignorado = importado(usuario, horizonte, "2026-11-05", "-9.90", "UBER* TRIP 0001");
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", ignorado)).hasStatus(204);

        MvcTestResult previa = api.get(usuario, "/api/regras/aplicacao");
        assertThat(previa).hasStatusOk().bodyJson().extractingPath("total").isEqualTo(4);
        assertThat(previa).bodyJson().extractingPath("$.grupos[*].regraId").asArray().containsExactly((int) regraDoEats, (int) regraDoUber);
        assertThat(previa).bodyJson().extractingPath("$.grupos[*].quantidade").asArray().containsExactly(1, 3);
        assertThat(previa).bodyJson().extractingPath("$.grupos[1].manuais").isEqualTo(1);
        assertThat(previa).bodyJson().extractingPath("$.grupos[1].renomeados").isEqualTo(1);
        assertThat(previa).bodyJson().extractingPath("$.grupos[1].categoriaNome").isEqualTo("App de transporte");
        assertThat(previa).bodyJson().extractingPath("$.grupos[1].categoriaMaeNome").isEqualTo("Transporte");
        assertThat(previa).bodyJson().extractingPath("$.grupos[1].exemplos[*].descricao").asArray()
                .containsExactly("PAG*UBERTRIP 8812", "Corrida do aeroporto", "Uber");
        assertThat(previa).bodyJson().extractingPath("$.grupos[1].exemplos[0].contaNome").isEqualTo("Banco Horizonte");
        assertThat(api.get(usuario, "/api/regras/aplicacao?regra={id}", regraDoUber)).bodyJson()
                .extractingPath("$.grupos[*].regraId").asArray().containsExactly((int) regraDoUber);

        MvcTestResult aplicados = api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regraDoEats + "," + regraDoUber + "]}");
        assertThat(aplicados).hasStatusOk().bodyJson().extractingPath("categorizados").isEqualTo(4);
        assertThat(categoriaDoLancamento(eats)).isEqualTo(delivery);
        assertThat(descricaoDoLancamento(eats)).isEqualTo("UBER EATS*PEDIDO 991");
        assertThat(categoriaDoLancamento(trip)).isEqualTo(app);
        assertThat(descricaoDoLancamento(trip)).isEqualTo("Uber");
        assertThat(jdbc.queryForObject("select descricao_banco from lancamentos where id = ?", String.class, trip)).isEqualTo("PAG*UBERTRIP 8812");
        assertThat(descricaoDoLancamento(editado)).isEqualTo("Corrida do aeroporto");
        assertThat(categoriaDoLancamento(aMao)).isEqualTo(app);
        assertThat(descricaoDoLancamento(aMao)).isEqualTo("Uber");
        assertThat(categoriaDoLancamento(escolhido)).isEqualTo(lazer);
        assertThat(categoriaDoLancamento(estorno)).isNull();
        assertThat(categoriaDoLancamento(ignorado)).isNull();

        // Aplicar de novo não acha nada: o que já tem categoria não muda.
        assertThat(api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regraDoUber + "]}")).hasStatusOk().bodyJson()
                .extractingPath("categorizados").isEqualTo(0);
        assertThat(api.get(usuario, "/api/regras/aplicacao")).bodyJson().extractingPath("total").isEqualTo(0);
    }

    @Test
    void desmarcarUmaRegraNaoPassaOsLancamentosDelaParaADeBaixo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long regraDoUber = criarRegra(usuario, "CONTEM", "uber", null, idDaCategoria(usuario, "App de transporte"), null);
        long regraDoEats = criarRegra(usuario, "CONTEM", "uber eats", null, idDaCategoria(usuario, "Delivery"), null);
        long eats = importado(usuario, horizonte, "2026-11-08", "-52.00", "UBER EATS*PEDIDO 991");
        long trip = importado(usuario, horizonte, "2026-11-09", "-23.90", "PAG*UBERTRIP 8812");

        assertThat(api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regraDoUber + "]}")).hasStatusOk().bodyJson()
                .extractingPath("categorizados").isEqualTo(1);
        assertThat(categoriaDoLancamento(eats)).isNull();
        assertThat(categoriaDoLancamento(trip)).isNotNull();
        assertCampo(api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[]}"), "regras", "Escolha ao menos uma regra.");
        String duzentasEUma = LongStream.rangeClosed(1, 201).mapToObj(String::valueOf).collect(Collectors.joining(","));
        assertCampo(api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + duzentasEUma + "]}"), "regras",
                "Envie no máximo 200 regras.");

        // A regra excluída (em outra aba, por exemplo) não some em silêncio: 422, e nada muda.
        assertThat(api.delete(usuario, "/api/regras/{id}", regraDoEats)).hasStatus(204);
        assertThat(api.get(usuario, "/api/regras/aplicacao?regra={id}", regraDoEats)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Regra não encontrada.");
        assertThat(api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regraDoUber + "," + regraDoEats + "]}"))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Regra não encontrada.");
        assertThat(categoriaDoLancamento(eats)).isNull();
    }

    @Test
    void regraPausadaEContaArquivadaFicamDeFora() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança");
        long lazer = idDaCategoria(usuario, "Lazer");
        criarRegra(usuario, "CONTEM", "cinema", null, lazer, null);
        criarRegra(usuario, "CONTEM", "mercado", null, idDaCategoria(usuario, "Mercado"), null);
        importado(usuario, horizonte, "2026-11-08", "-64.00", "CINEMA ARCOPLEX");
        importado(usuario, poupanca, "2026-11-08", "-87.40", "MERCADO BOM PRECO");
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatusOk();
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", poupanca)).hasStatusOk();

        assertThat(api.get(usuario, "/api/regras/aplicacao")).hasStatusOk().bodyJson().extractingPath("total").isEqualTo(0);
    }

    @Test
    void aplicarGravaEmLotesMaisDeMilLancamentos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long alimentacao = idDaCategoria(usuario, "Alimentação");
        long regra = criarRegra(usuario, "CONTEM", "padaria", null, alimentacao, null);
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, date '2026-11-01' + (g % 28), 'PADARIA ' || g, 'padaria ' || g, -1, 'DESPESA', 'MANUAL' "
                + "from generate_series(1, 2001) g", usuario.id(), horizonte);

        assertThat(api.get(usuario, "/api/regras/aplicacao")).hasStatusOk().bodyJson().extractingPath("total").isEqualTo(2001);
        assertThat(api.post(usuario, "/api/regras/aplicacao", "{\"regras\":[" + regra + "]}")).hasStatusOk().bodyJson()
                .extractingPath("categorizados").isEqualTo(2001);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ? and categoria_id = ?", Integer.class,
                usuario.id(), alimentacao)).isEqualTo(2001);
        assertThat(api.get(usuario, "/api/regras/aplicacao")).bodyJson().extractingPath("total").isEqualTo(0);
    }

    @Test
    void categorizarNuncaTrocaUmaCategoriaNemTocaEmTransferencia() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança");
        long lazer = idDaCategoria(usuario, "Lazer");
        long app = idDaCategoria(usuario, "App de transporte");
        long comCategoria = manual(usuario, horizonte, "DESPESA", "10.00", "Uber", lazer);
        long receita = manual(usuario, horizonte, "RECEITA", "10.00", "Uber reembolso", null);
        long transferencia = api.idCriado(api.post(usuario, "/api/transferencias",
                transferencia("50.00", "2026-11-05", horizonte, poupanca, null)));
        long lado = jdbc.queryForObject("select min(id) from lancamentos where transferencia_id = ?", Long.class, transferencia);

        int mudaram = transacao.execute(status -> lancamentosParaRegras.categorizar(usuario.id(), List.of(
                new LancamentosParaRegras.Categorizacao(comCategoria, app, "Outra"),
                new LancamentosParaRegras.Categorizacao(receita, app, null),
                new LancamentosParaRegras.Categorizacao(lado, app, null))));

        assertThat(mudaram).isZero();
        assertThat(categoriaDoLancamento(comCategoria)).isEqualTo(lazer);
        assertThat(descricaoDoLancamento(comCategoria)).isEqualTo("Uber");
        assertThat(categoriaDoLancamento(receita)).isNull();
        assertThat(categoriaDoLancamento(lado)).isNull();
    }
}
