package com.flux.categorizacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Criar regra" (decisões 20 a 22 da 04b): o texto sugerido sai daqui, e só para o que nenhuma regra pega.
 * A categoria arquivada fica sem sugestão; a categoria ou a conta que não é do usuário dá 422.
 */
class SugestoesDeRegraIT extends RegrasBase {

    private static String item(long contaId, String descricaoBanco, String descricao, long categoriaId) {
        return "{\"contaId\":" + contaId + ",\"descricaoBanco\":\"" + descricaoBanco + "\",\"descricao\":"
                + (descricao == null ? "null" : "\"" + descricao + "\"") + ",\"categoriaId\":" + categoriaId + "}";
    }

    @Test
    void sugereOTextoAteONumeroComACategoriaEALegivel() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long app = idDaCategoria(usuario, "App de transporte");
        long alimentacao = idDaCategoria(usuario, "Alimentação");

        MvcTestResult resposta = api.post(usuario, "/api/regras/sugestoes", "{\"itens\":[" + item(horizonte, "PAG*UBERTRIP 8812", null, app)
                + "," + item(horizonte, "PIX QRS PADARIA CE03/12", "Padaria Central", alimentacao) + ","
                + item(horizonte, "PIX QRS PADARIA CE26/11", "pix qrs padaria ce26/11", alimentacao) + "]}");

        assertThat(resposta).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(2);
        assertThat(resposta).bodyJson().extractingPath("$[0].condicao").isEqualTo("CONTEM");
        assertThat(resposta).bodyJson().extractingPath("$[0].texto").isEqualTo("PAG*UBERTRIP");
        assertThat(resposta).bodyJson().extractingPath("$[0].categoriaNome").isEqualTo("App de transporte");
        assertThat(resposta).bodyJson().extractingPath("$[0].categoriaMaeNome").isEqualTo("Transporte");
        assertThat(resposta).bodyJson().extractingPath("$[0].natureza").isEqualTo("DESPESA");
        assertThat(resposta).bodyJson().extractingPath("$[0].descricao").isNull();
        assertThat(resposta).bodyJson().extractingPath("$[0].deDescricao").isEqualTo("PAG*UBERTRIP 8812");
        assertThat(resposta).bodyJson().extractingPath("$[1].texto").isEqualTo("PIX QRS PADARIA");
        assertThat(resposta).bodyJson().extractingPath("$[1].descricao").isEqualTo("Padaria Central");
    }

    @Test
    void naoSugereOQueUmaRegraJaPegaNemCategoriaArquivadaNemMaisDeCinco() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long poupanca = novaConta(usuario, "Poupança");
        long app = idDaCategoria(usuario, "App de transporte");
        long lazer = idDaCategoria(usuario, "Lazer");
        long compras = idDaCategoria(usuario, "Compras");
        criarRegra(usuario, "CONTEM", "uber", null, app, null);
        criarRegra(usuario, "CONTEM", "cinema", null, lazer, null);
        criarRegra(usuario, "CONTEM", "farmacia", poupanca, compras, null);
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", lazer)).hasStatusOk();

        StringBuilder itens = new StringBuilder("{\"itens\":[");
        itens.append(item(horizonte, "UBER* TRIP 5521", null, app)).append(',');
        itens.append(item(horizonte, "CINEMA 0001", null, compras)).append(',');
        itens.append(item(horizonte, "TEATRO MUNICIPAL", null, lazer)).append(',');
        itens.append(item(horizonte, "FARMACIA BEM ESTAR", null, compras)).append(',');
        itens.append(item(horizonte, "FARMACIA BEM ESTAR 2", null, compras)).append(',');
        for (String loja : new String[] {"LOJA AZUL", "LOJA VERDE", "LOJA ROXA", "LOJA CINZA", "LOJA OURO"}) {
            itens.append(item(horizonte, loja, null, compras)).append(',');
        }
        itens.setLength(itens.length() - 1);
        MvcTestResult resposta = api.post(usuario, "/api/regras/sugestoes", itens.append("]}").toString());

        // UBER: a regra ativa já pega. TEATRO: a categoria está arquivada. CINEMA: a regra igual existe, mesmo
        // pausada. FARMACIA: a regra só da Poupança não pega em Banco Horizonte; o segundo FARMACIA repete o texto.
        assertThat(resposta).hasStatusOk().bodyJson().extractingPath("$[*].texto").asArray()
                .containsExactly("FARMACIA BEM ESTAR", "LOJA AZUL", "LOJA VERDE", "LOJA ROXA", "LOJA CINZA");

        // Categoria ou conta que não existe (ou de outro usuário, IsolamentoDeRegrasIT) não é pulada: 422.
        assertThat(api.post(usuario, "/api/regras/sugestoes", "{\"itens\":[" + item(horizonte, "LOJA AZUL", null, compras) + ","
                + item(horizonte, "LOJA VERDE", null, 999_999_999L) + "]}")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(usuario, "/api/regras/sugestoes", "{\"itens\":[" + item(999_999_999L, "LOJA AZUL", null, compras) + "]}"))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
    }
}
