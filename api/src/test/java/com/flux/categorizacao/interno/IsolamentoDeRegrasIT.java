package com.flux.categorizacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0007: B não lista, não edita, não exclui, não reordena, não vê a prévia nem aplica as regras de
 * A, não usa a categoria nem a conta de A numa regra, numa prévia ou numa sugestão, e as regras de A
 * não agem sobre os lançamentos de B (nem os de B sobre os de A). Cada endpoint de /api/regras é
 * exercitado com o dado do outro: 404 na URL e 422 no corpo, pelo ADR 0007; 422 na ?regra= da prévia do
 * "Aplicar", por decisão desta fatia (ADR 0015).
 */
class IsolamentoDeRegrasIT extends RegrasBase {

    @Test
    void cadaEndpointDeRegrasIsolaOsUsuarios() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        long contaDeA = novaConta(a, "Banco Horizonte");
        long appDeA = idDaCategoria(a, "App de transporte");
        long regraDeA = criarRegra(a, "CONTEM", "uber", null, appDeA, "Uber");
        long lancamentoDeA = importado(a, contaDeA, "2026-11-09", "-23.90", "PAG*UBERTRIP 8812");
        long contaDeB = novaConta(b, "Banco Aurora");
        long appDeB = idDaCategoria(b, "App de transporte");
        long lancamentoDeB = importado(b, contaDeB, "2026-11-09", "-18.40", "UBER* TRIP 5521");

        assertThat(api.get(b, "/api/regras")).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(0);
        long regraDeB = criarRegra(b, "CONTEM", "padaria", null, appDeB, null);
        assertThat(api.put(b, "/api/regras/{id}", regra("CONTEM", "uber", null, appDeB, null), regraDeA)).hasStatus(404).bodyJson()
                .extractingPath("detail").isEqualTo("Regra não encontrada.");
        assertThat(api.delete(b, "/api/regras/{id}", regraDeA)).hasStatus(404);
        assertThat(api.put(b, "/api/regras/ordem", "{\"ids\":[" + regraDeA + "]}")).hasStatus(422).bodyJson()
                .extractingPath("codigo").isEqualTo("ORDEM_DESATUALIZADA");
        assertThat(api.post(b, "/api/regras", regra("CONTEM", "uber", null, appDeA, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(b, "/api/regras", regra("CONTEM", "uber", contaDeA, appDeB, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.put(b, "/api/regras/{id}", regra("CONTEM", "padaria", null, appDeA, null), regraDeB)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.put(b, "/api/regras/{id}", regra("CONTEM", "padaria", contaDeA, appDeB, null), regraDeB)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.post(b, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"ubertrip\",\"contaId\":null,"
                + "\"categoriaId\":" + appDeA + "}")).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(b, "/api/regras/previa", "{\"condicao\":\"CONTEM\",\"texto\":\"ubertrip\",\"contaId\":" + contaDeA
                + ",\"categoriaId\":null}")).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.get(b, "/api/regras/aplicacao")).hasStatusOk().bodyJson().extractingPath("total").isEqualTo(0);
        assertThat(api.get(b, "/api/regras/aplicacao?regra={id}", regraDeA)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Regra não encontrada.");
        assertThat(api.post(b, "/api/regras/aplicacao", "{\"regras\":[" + regraDeB + "," + regraDeA + "]}")).hasStatus(422).bodyJson()
                .extractingPath("codigo").isEqualTo("REGRA_INVALIDA");
        assertThat(api.post(b, "/api/regras/sugestoes", "{\"itens\":[{\"contaId\":" + contaDeB + ",\"descricaoBanco\":\"PADARIA\","
                + "\"descricao\":null,\"categoriaId\":" + appDeA + "}]}")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(b, "/api/regras/sugestoes", "{\"itens\":[{\"contaId\":" + contaDeA + ",\"descricaoBanco\":\"PADARIA\","
                + "\"descricao\":null,\"categoriaId\":" + appDeB + "}]}")).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(categoriaDoLancamento(lancamentoDeA)).isNull();
        assertThat(categoriaDoLancamento(lancamentoDeB)).isNull();

        // A aplica as regras dele: só o lançamento de A muda.
        assertThat(api.post(a, "/api/regras/aplicacao", "{\"regras\":[" + regraDeA + "]}")).hasStatusOk().bodyJson()
                .extractingPath("categorizados").isEqualTo(1);
        assertThat(categoriaDoLancamento(lancamentoDeA)).isEqualTo(appDeA);
        assertThat(categoriaDoLancamento(lancamentoDeB)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from regras_categorizacao where usuario_id = ?", Integer.class, a.id())).isEqualTo(1);
        assertThat(api.get(b, "/api/regras")).bodyJson().extractingPath("$[0].categoriaId").isEqualTo((int) appDeB);
    }
}
