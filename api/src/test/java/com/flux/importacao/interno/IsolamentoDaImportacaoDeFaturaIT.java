package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c (decisão 51): o usuário B não importa no cartão de A, não lê nem grava o perfil do cartão de A, não vê nem
 * desfaz a importação de fatura de A: 404 na URL e 422 no corpo, com a mensagem do inexistente (ADR 0007).
 */
class IsolamentoDaImportacaoDeFaturaIT extends FaturaBase {

    @Test
    void oUsuarioBNaoUsaOCartaoNemAImportacaoDeA() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        long horizonte = novoCartao(a, "Cartão Horizonte", 3, 10, novaConta(a, "Banco Horizonte"));
        long deB = novoCartao(b, "Cartão de B", 3, 10, novaConta(b, "Conta de B"));
        perfilDoNubank(a, horizonte);
        byte[] fatura = csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"));
        long importacao = importarNoCartao(a, horizonte, "fatura.csv", fatura, 2);
        String hash = api.json(previaNoCartao(a, horizonte, null, "fatura.csv", fatura)).get("hash").asString();

        MvcTestResult previa = previaNoCartao(b, horizonte, null, "fatura.csv", fatura);
        assertThat(previa).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
        // O mesmo corpo do cartão que não existe: B não descobre que o cartão de A existe.
        MvcTestResult inexistente = previaNoCartao(b, 999_999_999L, null, "fatura.csv", fatura);
        assertThat(api.json(previa)).isEqualTo(api.json(inexistente));
        MvcTestResult confirmacao = confirmarNoCartao(b, horizonte, m().toString(), "fatura.csv", fatura, hash, escolhas(2));
        assertThat(confirmacao).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(api.get(b, "/api/importacoes/perfis/cartao/{id}", horizonte)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Cartão não encontrado.");
        assertThat(api.put(b, "/api/importacoes/perfis/cartao/{id}", NUBANK_FATURA, horizonte)).hasStatus(404);
        assertThat(api.get(b, "/api/importacoes/perfis/cartao/{id}", deB)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Este cartão ainda não tem perfil de CSV.");
        assertThat(api.acao(b, "/api/importacoes/{id}/desfazer", importacao)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Importação não encontrada.");
        assertThat(api.get(b, "/api/importacoes")).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(contar("compras", a)).isEqualTo(1);
        assertThat(contar("compras", b)).isZero();
        assertThat(contar("importacoes", b)).isZero();
    }
}
