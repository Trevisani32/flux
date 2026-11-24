package com.flux.relatorios.interno;

import java.time.LocalDate;
import java.time.YearMonth;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

abstract class RelatoriosBase extends CartoesBase {

    private final JsonMapper jsonExato = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    protected YearMonth m;

    protected LocalDate fixarOMesAtual() {
        LocalDate hoje = fixarHojeNoDia10();
        m = YearMonth.from(hoje);
        return hoje;
    }

    protected String d(int mesesAtras, int dia) {
        return m.minusMonths(mesesAtras).atDay(dia).toString();
    }

    protected String mes(int mesesAtras) {
        return m.minusMonths(mesesAtras).toString();
    }

    protected JsonNode relatorio(UsuarioDeTeste usuario, String mes) {
        return json(api.get(usuario, "/api/relatorios/mes?mes=" + mes));
    }

    protected JsonNode relatorio(UsuarioDeTeste usuario) {
        return json(api.get(usuario, "/api/relatorios/mes"));
    }

    protected JsonNode json(MvcTestResult resposta) {
        return jsonExato.readTree(texto(resposta));
    }

    protected long importadoIgnorado(UsuarioDeTeste usuario, long contaId, String data, String valor) {
        Long importacao = jdbc.queryForObject(
                "insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, "
                        + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) "
                        + "values (?, ?, 'OFX', 'teste.ofx', md5(random()::text) || md5(random()::text), "
                        + "?::date, ?::date, 1, 1, 0, 0, 0, 0) returning id",
                Long.class,
                usuario.id(),
                contaId,
                data,
                data);
        long id = jdbc.queryForObject(
                "insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem, "
                        + "importacao_id, chave_importacao, descricao_banco) values (?, ?, ?::date, 'Ignorado', "
                        + "'ignorado', ?::numeric, 'DESPESA', 'IMPORTADO', ?, 'ID:' || md5(random()::text), "
                        + "'Ignorado') returning id",
                Long.class,
                usuario.id(),
                contaId,
                data,
                valor,
                importacao);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", id)).hasStatus(204);
        return id;
    }

    protected MesDeReferencia montarOMesDeReferencia(UsuarioDeTeste usuario) {
        long contaA = api.idCriado(api.post(usuario, "/api/contas", conta("Conta A", "CORRENTE", "1000", d(4, 1), "null")));
        long investimento = api.idCriado(
                api.post(usuario, "/api/contas", conta("Invest", "INVESTIMENTO", "5000", d(4, 1), "null")));
        long contaB = api.idCriado(api.post(usuario, "/api/contas", conta("Conta B", "POUPANCA", "200", d(3, 10), "null")));
        long salario = idDaCategoria(usuario, "Salário");
        long aluguel = idDaCategoria(usuario, "Aluguel");
        long moradia = idDaCategoria(usuario, "Moradia");
        long restaurante = idDaCategoria(usuario, "Restaurante");
        long rendimentos = idDaCategoria(usuario, "Rendimentos");
        long reembolsos = idDaCategoria(usuario, "Reembolsos");
        long lazer = idDaCategoria(usuario, "Lazer");

        criarLancamento(usuario, "RECEITA", "3000", d(3, 5), "Salário", contaA, salario);
        criarLancamento(usuario, "DESPESA", "1000", d(3, 6), "Aluguel", contaA, aluguel);
        criarLancamento(usuario, "RECEITA", "3000", d(2, 5), "Salário", contaA, salario);
        criarLancamento(usuario, "DESPESA", "900", d(2, 7), "Aluguel", contaA, aluguel);
        criarLancamento(usuario, "DESPESA", "50", d(2, 8), "Moradia direto", contaA, moradia);
        criarLancamento(usuario, "RECEITA", "50", d(2, 5), "Rendimento", contaB, rendimentos);
        criarLancamento(usuario, "RECEITA", "3300", d(1, 5), "Salário", contaA, salario);
        criarLancamento(usuario, "DESPESA", "1100", d(1, 7), "Aluguel", contaA, aluguel);
        assertThat(api.post(usuario, "/api/transferencias", transferencia("300", d(1, 9), contaA, investimento, null)))
                .hasStatus(201);
        criarLancamento(usuario, "RECEITA", "3000", d(0, 5), "Salário", contaA, salario);
        criarLancamento(usuario, "DESPESA", "1200", d(0, 6), "Aluguel", contaA, aluguel);
        criarLancamento(usuario, "DESPESA", "60.50", d(0, 7), "Moradia direto", contaA, moradia);
        criarLancamento(usuario, "DESPESA", "25", d(0, 8), "Sem categoria", contaA, null);
        importadoIgnorado(usuario, contaA, d(0, 9), "-40");
        criarLancamento(usuario, "DESPESA", "10", d(0, 20), "Futuro", contaA, lazer);

        long cartao = api.idCriado(api.post(usuario, "/api/cartoes", cartao("Cartão K", "1000", 5, 15, contaA, "null")));
        assertThat(api.post(usuario, "/api/compras", compra(cartao, d(2, 3), "Restaurante 3x", "300", 3, 1, restaurante)))
                .hasStatus(201);
        assertThat(api.post(usuario, "/api/estornos", estorno(cartao, d(1, 2), "Estorno", "40", reembolsos))).hasStatus(201);
        assertThat(api.post(
                usuario,
                "/api/cartoes/{id}/faturas/{mes}/pagamentos",
                "{\"contaId\":" + contaA + ",\"data\":\"" + d(2, 20) + "\",\"valor\":100,\"observacao\":null}",
                cartao,
                mes(2))).hasStatus(201);
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", contaB)).hasStatus(200);
        jdbc.update(
                "update contas set arquivada_em = ?::timestamptz where id = ?",
                m.minusMonths(1).atDay(1) + "T02:30:00Z",
                contaB);
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatus(200);

        return new MesDeReferencia(contaA, investimento, contaB, cartao, moradia, aluguel, lazer);
    }

    private void criarLancamento(
            UsuarioDeTeste usuario,
            String tipo,
            String valor,
            String data,
            String descricao,
            long contaId,
            Long categoriaId) {
        assertThat(api.post(usuario, "/api/lancamentos", lancamento(tipo, valor, data, descricao, contaId, categoriaId)))
                .hasStatus(201);
    }

    protected record MesDeReferencia(
            long contaA,
            long investimento,
            long contaB,
            long cartao,
            long moradia,
            long aluguel,
            long lazer) {
    }
}
