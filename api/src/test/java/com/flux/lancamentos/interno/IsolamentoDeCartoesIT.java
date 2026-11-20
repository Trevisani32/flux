package com.flux.lancamentos.interno;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0007 e decisão 51 da 05a: o usuário B não lista, não lê, não edita, não exclui nem referencia no corpo o
 * cartão, a fatura, a compra e o estorno de A. 404 na URL e 422 no corpo, com a mensagem do inexistente.
 */
class IsolamentoDeCartoesIT extends CartoesBase {

    @Test
    void outroUsuarioNaoAlcancaCartaoFaturaCompraNemEstorno() {
        UsuarioDeTeste dona = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long contaDaDona = novaConta(dona, "Banco Horizonte");
        long cartao = novoCartao(dona, "Cartão Horizonte", 3, 10, contaDaDona);
        long compra = novaCompra(dona, cartao, "2025-11-09", "Loja", "300", 3);
        long devolucao = api.idCriado(api.post(dona, "/api/estornos", estorno(cartao, "2025-11-10", "Estorno", "10", null)));
        long contaDoOutro = novaConta(outro, "Conta do outro");
        long cartaoDoOutro = novoCartao(outro, "Cartão do outro", 3, 10, contaDoOutro);

        assertThat(api.get(outro, "/api/cartoes")).bodyJson().extractingPath("ativos[*].nome").asArray().containsExactly("Cartão do outro");
        assertThat(api.get(outro, "/api/cartoes/{id}", cartao)).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(api.put(outro, "/api/cartoes/{id}", cartao("X", "1", 3, 10, contaDoOutro, "null"), cartao)).hasStatus(404);
        assertThat(api.acao(outro, "/api/cartoes/{id}/arquivar", cartao)).hasStatus(404);
        assertThat(api.acao(outro, "/api/cartoes/{id}/reativar", cartao)).hasStatus(404);
        assertThat(api.delete(outro, "/api/cartoes/{id}", cartao)).hasStatus(404);
        assertThat(api.post(outro, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":13,\"diaVencimento\":20}", cartao)).hasStatus(404);
        assertThat(api.get(outro, "/api/cartoes/{id}/faturas/{mes}", cartao, "2025-12")).hasStatus(404);
        assertThat(api.put(outro, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-12-04\"}", cartao, "2025-12")).hasStatus(404);
        assertThat(api.delete(outro, "/api/cartoes/{id}/faturas/{mes}/fechamento", cartao, "2025-12")).hasStatus(404);
        assertThat(api.get(outro, "/api/compras/{id}", compra)).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Compra não encontrada.");
        assertThat(api.put(outro, "/api/compras/{id}", compra(cartaoDoOutro, "2025-11-09", "X", "1", 1, 1, null), compra)).hasStatus(404);
        assertThat(api.delete(outro, "/api/compras/{id}", compra)).hasStatus(404);
        assertThat(api.get(outro, "/api/estornos/{id}", devolucao)).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Estorno não encontrado.");
        assertThat(api.put(outro, "/api/estornos/{id}", estorno(cartaoDoOutro, "2025-11-09", "X", "1", null), devolucao)).hasStatus(404);
        assertThat(api.delete(outro, "/api/estornos/{id}", devolucao)).hasStatus(404);
        long parcela = jdbc.queryForObject("select id from lancamentos where compra_id = ? and numero_parcela = 1", Long.class, compra);
        assertThat(api.get(outro, "/api/lancamentos/{id}", parcela)).hasStatus(404);
        long compraDoOutro = novaCompra(outro, cartaoDoOutro, "2025-11-09", "Do outro", "30", 1);
        assertThat(api.put(outro, "/api/compras/{id}", compra(cartao, "2025-11-09", "Do outro", "30", 1, 1, null), compraDoOutro))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");

        assertThat(api.post(outro, "/api/compras", compra(cartao, "2025-11-09", "X", "1", 1, 1, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(api.post(outro, "/api/estornos", estorno(cartao, "2025-11-09", "X", "1", null))).hasStatus(422);
        assertThat(api.post(outro, "/api/compras/previa", compra(cartao, "2025-11-09", "X", "1", 1, 1, null))).hasStatus(422);
        assertThat(api.post(outro, "/api/compras", compra(cartaoDoOutro, "2025-11-09", "X", "1", 1, 1, idDaCategoria(dona, "Compras"))))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Categoria não encontrada.");
        assertThat(api.post(outro, "/api/cartoes", cartao("Cartão", "1", 3, 10, contaDaDona, "null"))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.get(outro, "/api/lancamentos?mes=2025-12&cartao=" + cartao)).hasStatus(200).bodyJson().extractingPath("dias").asArray().isEmpty();
        assertThat(api.get(outro, "/api/lancamentos?mes=2025-12&cartao=" + cartao)).bodyJson().extractingPath("fatura").isNull();
        assertThat(contar("lancamentos", outro)).isEqualTo(1);
        assertThat(parcelas(compra)).hasSize(3);
    }
}
