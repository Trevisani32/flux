package com.flux.exportacao.interno;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.flux.suporte.ApiDeTeste;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class ExportacaoIT extends ExportacaoBase {

    @Test
    void oJsonTemTudoQueOUsuarioGuardou() {
        UsuarioDeTeste usuario = criarUsuarioComHojeFixado();
        DadosDaExportacao dados = montarOsDados(usuario);
        UsuarioDeTeste outroUsuario = usuariosDeTeste.criar();

        MvcTestResult resposta = exportar(usuario, "JSON", usuario.senha());
        JsonNode arquivo = jsonDoArquivo(resposta);

        assertThat(resposta).hasStatusOk().hasContentType("application/json;charset=UTF-8");
        assertThat(arquivo.get("formato").asString()).isEqualTo(ArquivoJson.FORMATO);
        assertThat(arquivo.get("versao").asInt()).isEqualTo(1);
        assertThat(Duration.between(relogioDeTeste.instant(), Instant.parse(arquivo.get("geradoEm").asString())).abs())
                .isLessThanOrEqualTo(Duration.ofMinutes(1));
        assertThat(arquivo.get("foraDoArquivo").asString()).isEqualTo(CatalogoDaExportacao.FORA_DO_ARQUIVO);
        assertThat(arquivo.get("usuario").get("email").asString()).isEqualTo(usuario.email());
        assertThat(arquivo.get("usuario").get("nome").asString()).isEqualTo("Usuário de Teste");
        assertThat(arquivo.get("usuario").has("id")).isFalse();
        assertThat(arquivo.get("usuario").get("termosVersao").isNull()).isTrue();
        assertThat(arquivo.get("contas").size()).isEqualTo(contar("contas", usuario));
        assertThat(arquivo.get("categorias").size()).isEqualTo(contar("categorias", usuario));
        assertThat(arquivo.get("cartoes").size()).isEqualTo(contar("cartoes", usuario));
        assertThat(arquivo.get("faturas").size()).isEqualTo(contar("faturas", usuario));
        assertThat(arquivo.get("compras").size()).isEqualTo(contar("compras", usuario));
        assertThat(arquivo.get("lancamentos").size()).isEqualTo(contar("lancamentos", usuario));
        assertThat(arquivo.get("transferencias").size()).isEqualTo(contar("transferencias", usuario));
        assertThat(arquivo.get("importacoes").size()).isEqualTo(contar("importacoes", usuario));
        assertThat(arquivo.get("perfisCsv").size()).isEqualTo(contar("perfis_csv", usuario));
        assertThat(arquivo.get("regras").size()).isEqualTo(contar("regras_categorizacao", usuario));
        assertThat(arquivo.get("recorrencias").size()).isEqualTo(contar("recorrencias", usuario));
        assertThat(arquivo.get("ocorrenciasTratadas").size()).isEqualTo(contar("ocorrencias_tratadas", usuario));
        for (String lista : List.of("contas", "categorias", "cartoes", "faturas", "compras", "lancamentos", "transferencias",
                "importacoes", "perfisCsv", "regras", "recorrencias", "ocorrenciasTratadas")) {
            assertThat(ids(arquivo.get(lista))).isSorted();
        }

        JsonNode conta = porId(arquivo.get("contas"), dados.horizonte());
        assertThat(conta.get("saldoInicial").asString()).isEqualTo("1500.00");
        assertThat(decimal(conta.get("saldoInicial"))).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(conta.get("dataSaldoInicial").asString()).isEqualTo(dados.hoje().minusMonths(2).withDayOfMonth(1).toString());
        assertThat(porId(arquivo.get("contas"), dados.antiga()).get("arquivadaEm").isNull()).isFalse();
        assertThat(porId(arquivo.get("categorias"), dados.viagens()).get("arquivadaEm").isNull()).isFalse();

        JsonNode parcelaUm = porId(arquivo.get("lancamentos"), dados.parcelaUm());
        assertThat(parcelaUm.get("valor").asString()).isEqualTo("-150.00");
        assertThat(decimal(parcelaUm.get("valor"))).isEqualByComparingTo(new BigDecimal("-150.00"));
        assertThat(parcelaUm.get("cartaoId").asLong()).isEqualTo(dados.cartaoLua());
        assertThat(parcelaUm.get("faturaId").asLong()).isEqualTo(dados.faturaMesPassado());
        assertThat(parcelaUm.get("compraId").asLong()).isEqualTo(dados.compraSapataria());
        assertThat(parcelaUm.get("numeroParcela").asInt()).isEqualTo(1);
        assertThat(parcelaUm.get("contaId").isNull()).isTrue();
        assertThat(porId(arquivo.get("faturas"), dados.faturaMesPassado()).get("mesReferencia").asString())
                .isEqualTo(YearMonth.from(dados.hoje().minusMonths(1)).toString());
        assertThat(porId(arquivo.get("lancamentos"), dados.pagamento()).get("faturaPagaId").asLong())
                .isEqualTo(dados.faturaMesPassado());

        List<JsonNode> ladosDaTransferencia = arquivo.get("lancamentos").valueStream()
                .filter(lancamento -> lancamento.get("transferenciaId").asLong() == dados.transferencia()).toList();
        assertThat(ladosDaTransferencia).hasSize(2);
        assertThat(ladosDaTransferencia).allSatisfy(lancamento -> assertThat(lancamento.get("transferenciaId").asLong())
                .isEqualTo(dados.transferencia()));

        JsonNode importado = porId(arquivo.get("lancamentos"), dados.importadoIgnorado());
        assertThat(importado.get("ignoradoEm").isNull()).isFalse();
        assertThat(importado.get("origem").asString()).isEqualTo("IMPORTADO");
        assertThat(importado.get("importacaoId").asLong()).isEqualTo(dados.importacao());
        assertThat(importado.get("descricaoBanco").asString()).isEqualTo("PAG BOLETO");
        assertThat(porId(arquivo.get("importacoes"), dados.importacao()).get("finalDoExtrato").asString()).isEqualTo("4821");

        JsonNode assinatura = porId(arquivo.get("recorrencias"), dados.assinatura());
        assertThat(assinatura.get("descricao").asString()).isEqualTo("Assinatura Fictícia");
        assertThat(decimal(assinatura.get("valor"))).isEqualByComparingTo(new BigDecimal("29.90"));
        assertThat(assinatura.get("frequencia").asString()).isEqualTo("MENSAL");
        assertThat(assinatura.get("diaDoMes").asInt()).isEqualTo(12);
        assertThat(assinatura.get("ocorrenciasDesde").asString()).isEqualTo(dados.hoje().toString());
        JsonNode pulada = arquivo.get("ocorrenciasTratadas").get(0);
        assertThat(pulada.get("recorrenciaId").asLong()).isEqualTo(dados.assinatura());
        assertThat(pulada.get("situacao").asString()).isEqualTo("PULADA");
        assertThat(pulada.get("periodo").asString()).isEqualTo(YearMonth.from(dados.hoje()).atDay(1).toString());
        assertThat(pulada.get("lancamentoId").isNull()).isTrue();

        String texto = ApiDeTeste.texto(resposta);
        String senhaHash = jdbc.queryForObject("select senha_hash from usuarios where id = ?", String.class, usuario.id());
        String hashArquivo = jdbc.queryForObject("select hash_arquivo from importacoes where id = ?", String.class, dados.importacao());
        String extratoHash = jdbc.queryForObject("select extrato_hash from importacoes where id = ?", String.class, dados.importacao());
        String chaveImportacao = jdbc.queryForObject("select chave_importacao from lancamentos where id = ?", String.class,
                dados.importadoIgnorado());
        assertThat(texto).doesNotContain("senhaHash", "nomeChave", "descricaoChave", "chaveImportacao", "hashArquivo", "extratoHash",
                "textoChave", "usuarioId");
        assertThat(texto).doesNotContain("$2a$", senhaHash, hashArquivo, extratoHash, chaveImportacao, outroUsuario.email());
    }

    @Test
    void oCsvDosLancamentosAbreNoExcelEmPortugues() {
        UsuarioDeTeste usuario = criarUsuarioComHojeFixado();
        DadosDaExportacao dados = montarOsDados(usuario);

        MvcTestResult resposta = exportar(usuario, "CSV", usuario.senha());
        List<String> linhas = linhasDoCsv(resposta);
        LocalDate mesPassado = dados.hoje().minusMonths(1);
        String mesDaCompra = mesEAno(mesPassado);
        String mesDoPagamento = YearMonth.from(mesPassado).atDay(1)
                .format(DateTimeFormatter.ofPattern("MMMM 'de' uuuu", Locale.forLanguageTag("pt-BR")));

        assertThat(resposta).hasStatusOk().hasContentType("text/csv;charset=UTF-8");
        assertThat(linhas).hasSize(12);
        assertThat(linhas.get(0)).isEqualTo(ArquivoCsv.CABECALHO);
        assertThat(linhas.get(1)).isEqualTo("03/" + mesDaCompra + ";\"Café; pão \"\"quente\"\"\";-12,30;Despesa;Alimentação;Mercado;"
                + "Banco Horizonte;;;;\"linha 1\nlinha 2\";Manual;");
        assertThat(linhas.get(2)).isEqualTo("04/" + mesDaCompra + ";'=SOMA promoção;-35,90;Despesa;;;Banco Horizonte;;;;;Manual;");
        assertThat(linhas.get(3)).isEqualTo("05/" + mesDaCompra + ";Salário;8200,00;Receita;Salário;;Banco Horizonte;;;;;Manual;");
        assertThat(linhas.get(4)).isEqualTo("06/" + mesDaCompra + ";Reserva;-500,00;Transferência;;;Banco Horizonte;;;;;Manual;");
        assertThat(linhas.get(5)).isEqualTo("06/" + mesDaCompra + ";Reserva;500,00;Transferência;;;Poupança;;;;;Manual;");
        assertThat(linhas.get(6)).isEqualTo("15/" + mesDaCompra + ";Sapataria Passo Firme;-150,00;Despesa;Compras;;;Cartão Lua;1 de 3;02/"
                + mesDaCompra + ";;Manual;");
        assertThat(linhas.get(7)).isEqualTo("15/" + mesDaCompra + ";Estorno tênis;40,00;Receita;;;;Cartão Lua;;03/" + mesDaCompra
                + ";;Manual;");
        assertThat(linhas.get(8)).isEqualTo("20/" + mesDaCompra + ";Pagamento da fatura de " + mesDoPagamento
                + ";-100,00;Pagamento de fatura;;;Banco Horizonte;Cartão Lua;;;;Manual;");
        assertThat(linhas.get(9)).isEqualTo("15/" + mesEAno(dados.hoje()) + ";Sapataria Passo Firme;-150,00;Despesa;Compras;;;Cartão Lua;"
                + "2 de 3;02/" + mesDaCompra + ";;Manual;");
        assertThat(linhas.get(10)).isEqualTo("15/" + mesEAno(dados.hoje().plusMonths(1))
                + ";Sapataria Passo Firme;-150,00;Despesa;Compras;;;Cartão Lua;3 de 3;02/" + mesDaCompra + ";;Manual;");
        assertThat(linhas.get(11)).isEmpty();
        assertThat(linhas).noneMatch(linha -> linha.contains("PAG BOLETO"));
    }

    @Test
    void osCabecalhosUsamOHojeDeSaoPaulo() {
        LocalDate hoje = fixarHojeNoDia10();
        var instanteDoDia10 = hoje.atTime(23, 30).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        relogioDeTeste.avancar(Duration.between(relogioDeTeste.instant(), instanteDoDia10));
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        MvcTestResult json = exportar(usuario, "JSON", usuario.senha());
        MvcTestResult csv = exportar(usuario, "CSV", usuario.senha());

        assertThat(json).hasStatusOk().hasContentType("application/json;charset=UTF-8")
                .containsHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(json.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"flux-dados-" + hoje + ".json\"");
        assertThat(csv).hasStatusOk().hasContentType("text/csv;charset=UTF-8")
                .containsHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(csv.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"flux-lancamentos-" + hoje + ".csv\"");
        assertThat(json.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void pedidoInvalidoE400ESemTokenE401() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        MvcTestResult semFormato = mvc.post().uri("/api/minha-conta/exportacao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}").exchange();
        MvcTestResult formatoInvalido = mvc.post().uri("/api/minha-conta/exportacao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"formato\":\"PDF\",\"senhaAtual\":\"" + usuario.senha() + "\"}")
                .exchange();
        MvcTestResult senhaEmBranco = mvc.post().uri("/api/minha-conta/exportacao")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"formato\":\"JSON\",\"senhaAtual\":\"\"}").exchange();
        MvcTestResult semToken = mvc.post().uri("/api/minha-conta/exportacao")
                .contentType(MediaType.APPLICATION_JSON).content("{\"formato\":\"JSON\",\"senhaAtual\":\"x\"}").exchange();

        ApiDeTeste.assertCampo(semFormato, "formato", "Informe o formato.");
        assertThat(formatoInvalido).hasStatus(400).bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
        ApiDeTeste.assertCampo(senhaEmBranco, "senhaAtual", "Informe a senha atual.");
        assertThat(semToken).hasStatus(401);
        assertThat(semFormato.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(formatoInvalido.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(senhaEmBranco.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
        assertThat(semToken.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isNull();
    }

    private static List<Long> ids(JsonNode itens) {
        return itens.valueStream().map(item -> item.get("id").asLong()).toList();
    }

    private static String mesEAno(LocalDate data) {
        return "%02d/%d".formatted(data.getMonthValue(), data.getYear());
    }

    private static JsonNode porId(JsonNode itens, long id) {
        for (JsonNode item : itens) {
            if (item.get("id").asLong() == id) {
                return item;
            }
        }
        throw new AssertionError("Id não encontrado: " + id);
    }
}
