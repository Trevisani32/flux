package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 5, 33 e 51 da Fatia 05 (ADR 0009 e ADR 0018): o pagamento é uso da conta e do cartão, que não se excluem com
 * ele; excluir o usuário apaga pagamentos e faturas pagas fora do Flux sem esbarrar em nenhum RESTRICT.
 */
class ExclusaoComPagamentosIT extends PagamentosBase {

    @Test
    void oPagamentoSeguraAContaEOCartao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long carteira = novaConta(usuario, "Carteira", "CARTEIRA");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long compra = novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        long pagamento = api.idCriado(pagar(usuario, cartao, mesAtras(2), carteira, hoje().toString(), "100"));

        assertThat(api.delete(usuario, "/api/contas/{id}", carteira)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Esta conta não pode ser excluída porque tem 1 lançamento. Arquive a conta para tirá-la do saldo total e dos "
                        + "seletores.");
        MvcTestResult cartaoRecusado = api.delete(usuario, "/api/cartoes/{id}", cartao);
        assertThat(cartaoRecusado).hasStatus(422).bodyJson().extractingPath("uso").isEqualTo("1 compra e 1 pagamento");

        assertThat(api.delete(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/contas/{id}", carteira)).hasStatus(204);
        // A compra está na fatura que deixou de estar paga: agora ela se exclui, e o cartão também.
        assertThat(api.delete(usuario, "/api/compras/{id}", compra)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/cartoes/{id}", cartao)).hasStatus(204);
        assertThat(contar("faturas", usuario)).isZero();
    }

    @Test
    void excluirOUsuarioApagaPagamentosEFaturasPagasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        novaCompra(usuario, cartao, diaUmAtras(3), "Mercado", "312", 1);
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        pagarHoje(usuario, cartao, mesAtras(2), conta, "100");
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, mesAtras(3))).hasStatus(200);
        long despesa = importado(usuario, conta, hoje().toString(), "-50", "PAG FATURA");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartao + ",\"mes\":\"" + mesAtras(1)
                + "\"}", despesa)).hasStatus(201);
        long contaDoOutro = novaConta(outro, "Conta do outro");
        long cartaoDoOutro = cartaoQueFecha5(outro, "Cartão do outro", contaDoOutro);
        novaCompra(outro, cartaoDoOutro, diaUmAtras(2), "Do outro", "10", 1);
        pagarHoje(outro, cartaoDoOutro, mesAtras(2), contaDoOutro, "10");

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        for (String tabela : new String[] {"lancamentos", "compras", "faturas", "cartoes", "importacoes", "contas", "categorias"}) {
            assertThat(contar(tabela, usuario)).as(tabela).isZero();
        }
        assertThat(contar("lancamentos", outro)).isEqualTo(2);
        assertThat(fatura(outro, cartaoDoOutro, mesAtras(2))).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
    }
}
