package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0007 e decisão 51 da Fatia 05: o usuário B não lê, não edita, não exclui, não desfaz nem referencia no corpo o
 * pagamento, a fatura, a conta e a despesa importada de A. 404 na URL e 422 no corpo, com a mensagem do inexistente.
 */
class IsolamentoDePagamentosIT extends PagamentosBase {

    @Test
    void outroUsuarioNaoAlcancaPagamentoFaturaContaNemDespesaImportada() {
        UsuarioDeTeste dona = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long contaDaDona = novaConta(dona, "Banco Horizonte");
        long cartao = cartaoQueFecha5(dona, "Cartão Horizonte", contaDaDona);
        novaCompra(dona, cartao, diaUmAtras(2), "Livraria", "100", 1);
        String mes = mesAtras(2);
        long pagamento = pagarHoje(dona, cartao, mes, contaDaDona, "40");
        long despesa = importado(dona, contaDaDona, hoje().toString(), "-60", "PAG FATURA");
        long contaDoOutro = novaConta(outro, "Conta do outro");
        long cartaoDoOutro = cartaoQueFecha5(outro, "Cartão do outro", contaDoOutro);

        assertThat(api.get(outro, "/api/pagamentos/{id}", pagamento)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Pagamento não encontrado.");
        assertThat(api.put(outro, "/api/pagamentos/{id}", pagamento(contaDoOutro, hoje().toString(), "1"), pagamento)).hasStatus(404);
        assertThat(api.delete(outro, "/api/pagamentos/{id}", pagamento)).hasStatus(404);
        assertThat(api.acao(outro, "/api/pagamentos/{id}/desfazer", pagamento)).hasStatus(404);
        assertThat(pagar(outro, cartao, mes, contaDoOutro, hoje().toString(), "1")).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Cartão não encontrado.");
        assertThat(api.put(outro, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, mes)).hasStatus(404);
        assertThat(api.delete(outro, "/api/cartoes/{id}/faturas/{mes}/paga-fora", cartao, mes)).hasStatus(404);
        // A conta de A no corpo do pagamento de B: a mesma resposta da conta que não existe.
        assertThat(pagar(outro, cartaoDoOutro, mes, contaDaDona, hoje().toString(), "1")).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Conta não encontrada.");
        assertThat(api.get(outro, "/api/lancamentos/{id}/faturas-para-pagamento", despesa)).hasStatus(404);
        assertThat(api.post(outro, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartaoDoOutro + ",\"mes\":\"" + mes
                + "\"}", despesa)).hasStatus(404);
        // O pagamento de B pela A também não passa: o cartão de B no corpo da conversão de A.
        assertThat(api.post(dona, "/api/lancamentos/{id}/converter-em-pagamento", "{\"cartaoId\":" + cartaoDoOutro + ",\"mes\":\"" + mes
                + "\"}", despesa)).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");

        assertThat(fatura(dona, cartao, mes)).bodyJson().extractingPath("pagamentos[0].id").isEqualTo((int) pagamento);
        assertThat(fatura(outro, cartaoDoOutro, mes)).bodyJson().extractingPath("pagamentos").asArray().isEmpty();
    }
}
