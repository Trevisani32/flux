package com.flux.lancamentos.interno;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 5, 28 e 51 da 05a (ADR 0009): o cartão com compras e a categoria usada só por uma parcela não se excluem;
 * sem uso, as faturas vazias saem com o cartão; excluir o usuário apaga tudo dele sem esbarrar em nenhum RESTRICT.
 */
class ExclusaoComCartoesIT extends CartoesBase {

    @Test
    void oCartaoComComprasNaoSeExcluiEAsFaturasVaziasSaemComEle() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long educacao = idDaCategoria(usuario, "Educação");
        long compra = api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Curso", "300", 3, 1, educacao)));
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-10", "Estorno", "10", null)));
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2026-04-02\"}", horizonte, "2026-04"))
                .hasStatus(200);

        MvcTestResult recusada = api.delete(usuario, "/api/cartoes/{id}", horizonte);
        assertThat(recusada).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Este cartão não pode ser excluído porque tem 1 compra "
                + "e 1 estorno. Arquive o cartão para tirá-lo dos seletores.");
        assertThat(recusada).bodyJson().extractingPath("uso").isEqualTo("1 compra e 1 estorno");
        assertThat(recusada).bodyJson().extractingPath("codigo").isEqualTo("CARTAO_EM_USO");
        assertThat(api.delete(usuario, "/api/categorias/{id}", educacao)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Esta categoria não pode ser excluída porque tem 3 lançamentos. Arquive a categoria para tirá-la dos seletores.");

        assertThat(api.delete(usuario, "/api/compras/{id}", compra)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/estornos/{id}", devolucao)).hasStatus(204);
        assertThat(contar("faturas", usuario)).isEqualTo(4);
        assertThat(api.delete(usuario, "/api/cartoes/{id}", horizonte)).hasStatus(204);
        assertThat(contar("faturas", usuario)).isZero();
        assertThat(api.delete(usuario, "/api/categorias/{id}", educacao)).hasStatus(204);
    }

    @Test
    void excluirOUsuarioApagaCartoesFaturasComprasEParcelasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Curso", "300", 3, 1, idDaCategoria(usuario, "Educação"))));
        api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-10", "Estorno", "10", idDaCategoria(usuario, "Reembolsos"))));
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-12-04\"}", horizonte, "2025-12"))
                .hasStatus(200);
        long cartaoDoOutro = novoCartao(outro, "Cartão do outro", 3, 10, novaConta(outro, "Conta do outro"));
        novaCompra(outro, cartaoDoOutro, "2025-11-09", "Do outro", "10", 1);

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        for (String tabela : new String[] {"lancamentos", "compras", "faturas", "cartoes", "contas", "categorias"}) {
            assertThat(contar(tabela, usuario)).as(tabela).isZero();
        }
        assertThat(contar("lancamentos", outro)).isEqualTo(1);
        assertThat(contar("faturas", outro)).isEqualTo(1);
        assertThat(contar("cartoes", outro)).isEqualTo(1);
    }
}
