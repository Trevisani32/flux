package com.flux.exportacao.interno;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

abstract class ExportacaoBase extends CartoesBase {

    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    /** Cria o usuário depois de fixar o relógio que define todas as datas do cenário da exportação. */
    protected UsuarioDeTeste criarUsuarioComHojeFixado() {
        fixarHojeNoDia10();
        return usuariosDeTeste.criar();
    }

    protected MvcTestResult exportar(UsuarioDeTeste usuario, String formato, String senha) {
        return mvc.post().uri("/api/minha-conta/exportacao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formato\":\"" + formato + "\",\"senhaAtual\":\"" + senha + "\"}")
                .exchange();
    }

    protected byte[] bytes(MvcTestResult resposta) {
        return resposta.getResponse().getContentAsByteArray();
    }

    protected JsonNode jsonDoArquivo(MvcTestResult resposta) {
        return json.readTree(bytes(resposta));
    }

    protected List<String> linhasDoCsv(MvcTestResult resposta) {
        byte[] arquivo = bytes(resposta);
        assertThat(arquivo).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        String texto = new String(arquivo, 3, arquivo.length - 3, StandardCharsets.UTF_8);
        assertThat(texto).endsWith("\r\n");
        return Arrays.asList(texto.split("\\r\\n", -1));
    }

    protected DadosDaExportacao montarOsDados(UsuarioDeTeste usuario) {
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        LocalDate mesPassado = hoje.minusMonths(1);
        long horizonte = api.idCriado(api.post(usuario, "/api/contas",
                conta("Banco Horizonte", "CORRENTE", "1500.00", hoje.minusMonths(2).withDayOfMonth(1).toString(), "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas",
                conta("Poupança", "POUPANCA", "0.00", hoje.minusMonths(2).withDayOfMonth(1).toString(), "null")));
        long antiga = api.idCriado(api.post(usuario, "/api/contas",
                conta("Conta antiga", "CARTEIRA", "0.00", hoje.minusMonths(2).withDayOfMonth(1).toString(), "null")));
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", antiga)).hasStatusOk();

        long mercado = idDaCategoria(usuario, "Mercado");
        long salarioCategoria = idDaCategoria(usuario, "Salário");
        long compras = idDaCategoria(usuario, "Compras");
        long appDeTransporte = idDaCategoria(usuario, "App de transporte");
        long viagens = api.idCriado(api.post(usuario, "/api/categorias",
                "{\"nome\":\"Viagens\",\"natureza\":\"DESPESA\",\"categoriaMaeId\":null}"));
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", viagens)).hasStatusOk();

        long cafe = api.idCriado(api.post(usuario, "/api/lancamentos",
                lancamentoComObservacao("DESPESA", "12.30", mesPassado.withDayOfMonth(3), "Café; pão \\\"quente\\\"", horizonte, mercado,
                        "linha 1\\nlinha 2")));
        long somaPromocao = api.idCriado(api.post(usuario, "/api/lancamentos",
                lancamento("DESPESA", "35.90", mesPassado.withDayOfMonth(4).toString(), "=SOMA promoção", horizonte, null)));
        long salario = api.idCriado(api.post(usuario, "/api/lancamentos",
                lancamento("RECEITA", "8200.00", mesPassado.withDayOfMonth(5).toString(), "Salário", horizonte, salarioCategoria)));
        long transferencia = api.idCriado(api.post(usuario, "/api/transferencias",
                transferencia("500.00", mesPassado.withDayOfMonth(6).toString(), horizonte, poupanca, "Reserva")));

        long cartaoLua = api.idCriado(api.post(usuario, "/api/cartoes", cartao("Cartão Lua", "5000", 5, 15, horizonte, "null")));
        long compraSapataria = api.idCriado(api.post(usuario, "/api/compras",
                compra(cartaoLua, mesPassado.withDayOfMonth(2).toString(), "Sapataria Passo Firme", "450.00", 3, 1, compras)));
        long estornoTenis = api.idCriado(api.post(usuario, "/api/estornos",
                estorno(cartaoLua, mesPassado.withDayOfMonth(3).toString(), "Estorno tênis", "40.00", null)));
        String mesDaFatura = YearMonth.from(mesPassado).toString();
        long pagamento = api.idCriado(api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos",
                pagamento(horizonte, mesPassado.withDayOfMonth(20), "100.00"), cartaoLua, mesDaFatura));
        long faturaMesPassado = faturaDoCartao(usuario, cartaoLua, mesDaFatura);
        long faturaMesAtual = faturaDoCartao(usuario, cartaoLua, YearMonth.from(hoje).toString());
        long faturaMesSeguinte = faturaDoCartao(usuario, cartaoLua, YearMonth.from(hoje).plusMonths(1).toString());
        long parcelaUm = jdbc.queryForObject("select id from lancamentos where compra_id = ? and numero_parcela = 1", Long.class,
                compraSapataria);

        long importacao = jdbc.queryForObject(
                "insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, extrato_hash, extrato_final, "
                        + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (?, ?, 'OFX', "
                        + "'extrato-marco.ofx', md5(random()::text) || md5(random()::text), md5(random()::text) || md5(random()::text), "
                        + "'4821', ?::date, ?::date, 1, 1, 0, 0, 0, 0) returning id",
                Long.class, usuario.id(), horizonte, mesPassado.withDayOfMonth(7), mesPassado.withDayOfMonth(7));
        long importadoIgnorado = jdbc.queryForObject(
                "insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem, importacao_id, "
                        + "chave_importacao, descricao_banco) values (?, ?, ?::date, 'PAG BOLETO', 'pag boleto', -80.00, 'DESPESA', "
                        + "'IMPORTADO', ?, 'ID:' || ? || ':' || md5(random()::text), 'PAG BOLETO') returning id",
                Long.class, usuario.id(), horizonte, mesPassado.withDayOfMonth(7), importacao, horizonte);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", importadoIgnorado)).hasStatus(204);

        long regra = jdbc.queryForObject(
                "insert into regras_categorizacao (usuario_id, posicao, condicao, texto, texto_chave, natureza, categoria_id, descricao) "
                        + "values (?, 1, 'CONTEM', 'pag uber', 'pag uber', 'DESPESA', ?, 'Uber') returning id",
                Long.class, usuario.id(), appDeTransporte);
        long perfilCsv = jdbc.queryForObject(
                "insert into perfis_csv (usuario_id, conta_id, separador, linhas_de_cabecalho, coluna_data, formato_data, "
                        + "coluna_descricao, "
                        + "coluna_valor, coluna_saidas, coluna_identificador, separador_decimal) values (?, ?, 'PONTO_E_VIRGULA', 1, 1, "
                        + "'DD_MM_AAAA', 2, 3, null, null, 'VIRGULA') returning id",
                Long.class, usuario.id(), horizonte);
        long assinatura = api.idCriado(api.post(usuario, "/api/recorrencias",
                "{\"tipo\":\"DESPESA\",\"descricao\":\"Assinatura Fictícia\",\"valor\":29.90,\"valorVaria\":false,"
                        + "\"categoriaId\":" + compras + ",\"contaId\":" + horizonte + ",\"cartaoId\":null,\"frequencia\":\"MENSAL\","
                        + "\"diaDoMes\":12,\"comecaEm\":\"" + hoje + "\",\"terminaEm\":null}"));
        assertThat(api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/pular", assinatura,
                YearMonth.from(hoje).atDay(1))).hasStatusOk();

        return new DadosDaExportacao(horizonte, poupanca, antiga, mercado, salarioCategoria, compras, viagens, cartaoLua, compraSapataria,
                estornoTenis, faturaMesPassado, faturaMesAtual, faturaMesSeguinte, parcelaUm, pagamento, transferencia, cafe, somaPromocao,
                salario, importacao, importadoIgnorado, perfilCsv, regra, assinatura, hoje);
    }

    private static String lancamentoComObservacao(String tipo, String valor, LocalDate data, String descricao, long contaId,
                                                  long categoriaId,
                                                  String observacao) {
        return "{\"tipo\":\"" + tipo + "\",\"valor\":" + valor + ",\"data\":\"" + data + "\",\"descricao\":\"" + descricao
                + "\",\"contaId\":" + contaId + ",\"categoriaId\":" + categoriaId + ",\"observacao\":\"" + observacao + "\"}";
    }

    private static String pagamento(long contaId, LocalDate data, String valor) {
        return "{\"contaId\":" + contaId + ",\"data\":\"" + data + "\",\"valor\":" + valor + ",\"observacao\":null}";
    }

    private long faturaDoCartao(UsuarioDeTeste usuario, long cartaoId, String mes) {
        return jdbc.queryForObject("select id from faturas where usuario_id = ? and cartao_id = ? and mes_referencia = ?::date", Long.class,
                usuario.id(), cartaoId, mes + "-01");
    }

    protected BigDecimal decimal(JsonNode no) {
        return no.decimalValue();
    }

    protected record DadosDaExportacao(long horizonte, long poupanca, long antiga, long mercado, long salarioCategoria, long compras,
                                       long viagens, long cartaoLua, long compraSapataria, long estornoTenis, long faturaMesPassado,
                                       long faturaMesAtual, long faturaMesSeguinte, long parcelaUm, long pagamento, long transferencia,
                                       long cafe, long somaPromocao, long salario, long importacao, long importadoIgnorado, long perfilCsv,
                                       long regra, long assinatura, LocalDate hoje) {
    }
}
