package com.flux.exportacao.interno;

import java.util.List;
import java.util.stream.Collectors;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class IsolamentoDaExportacaoIT extends ExportacaoBase {

    private static final List<String> TABELAS = List.of("contas", "categorias", "cartoes", "faturas", "compras", "lancamentos",
            "transferencias", "importacoes", "perfis_csv", "regras_categorizacao", "recorrencias", "ocorrencias_tratadas");

    @Test
    void oUsuarioBNuncaRecebeDadoDoA() {
        UsuarioDeTeste usuarioA = criarUsuarioComHojeFixado();
        montarOsDados(usuarioA);
        JsonNode arquivoAAntes = jsonDoArquivo(exportar(usuarioA, "JSON", usuarioA.senha()));
        List<Integer> tamanhosDeA = tamanhosDasListas(arquivoAAntes);

        UsuarioDeTeste usuarioB = usuariosDeTeste.criar();
        long contaB = api.idCriado(api.post(usuarioB, "/api/contas", conta("Conta B", "CORRENTE", "0.00", "2025-01-01", "null")));
        api.idCriado(api.post(usuarioB, "/api/lancamentos", lancamento("DESPESA", "10.00", "2025-02-01", "Despesa B", contaB, null)));

        JsonNode arquivoB = jsonDoArquivo(exportar(usuarioB, "JSON", usuarioB.senha()));
        for (String tabela : TABELAS) {
            String lista = listaDoJson(tabela);
            List<Long> esperados = jdbc.queryForList(
                    "select id from " + tabela + " where usuario_id = ? order by id", Long.class, usuarioB.id());
            assertThat(ids(arquivoB.get(lista))).isEqualTo(esperados);
        }
        String textoB = new String(bytes(exportar(usuarioB, "JSON", usuarioB.senha())), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(arquivoB.get("usuario").get("email").asString()).isEqualTo(usuarioB.email());
        assertThat(textoB).doesNotContain(usuarioA.email(), "Banco Horizonte", "Sapataria", "pag uber");
        List<String> csvB = linhasDoCsv(exportar(usuarioB, "CSV", usuarioB.senha()));
        assertThat(csvB).hasSize(3);
        assertThat(csvB.get(1)).contains("Despesa B");
        assertThat(csvB.getLast()).isEmpty();

        JsonNode arquivoADepois = jsonDoArquivo(exportar(usuarioA, "JSON", usuarioA.senha()));
        assertThat(tamanhosDasListas(arquivoADepois)).isEqualTo(tamanhosDeA);
    }

    @Test
    void excluirAContaDeANaoMudaOArquivoDeB() {
        UsuarioDeTeste usuarioA = criarUsuarioComHojeFixado();
        montarOsDados(usuarioA);
        UsuarioDeTeste usuarioB = usuariosDeTeste.criar();
        api.idCriado(api.post(usuarioB, "/api/contas", conta("Conta B", "CORRENTE", "0.00", "2025-01-01", "null")));
        JsonNode antes = jsonDoArquivo(exportar(usuarioB, "JSON", usuarioB.senha()));

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuarioA.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuarioA.senha() + "\"}")).hasStatus(204);
        assertThat(exportar(usuarioA, "JSON", usuarioA.senha())).hasStatus(404)
                .bodyJson().extractingPath("detail").isEqualTo("Usuário não encontrado.");
        JsonNode depois = jsonDoArquivo(exportar(usuarioB, "JSON", usuarioB.senha()));
        ObjectNode antesSemData = (ObjectNode) antes.deepCopy();
        ObjectNode depoisSemData = (ObjectNode) depois.deepCopy();
        antesSemData.remove("geradoEm");
        depoisSemData.remove("geradoEm");
        assertThat(depoisSemData).isEqualTo(antesSemData);
    }

    private static List<Long> ids(JsonNode itens) {
        return itens.valueStream().map(item -> item.get("id").asLong()).collect(Collectors.toList());
    }

    private static List<Integer> tamanhosDasListas(JsonNode arquivo) {
        return TABELAS.stream().map(IsolamentoDaExportacaoIT::listaDoJson).map(lista -> arquivo.get(lista).size()).toList();
    }

    private static String listaDoJson(String tabela) {
        return switch (tabela) {
            case "perfis_csv" -> "perfisCsv";
            case "regras_categorizacao" -> "regras";
            case "ocorrencias_tratadas" -> "ocorrenciasTratadas";
            default -> tabela;
        };
    }
}
