package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0007 nos endpoints da 04c: B não vê os pares, não converte e não desfaz nada de A (404 na URL), e
 * não liga o que é dele a um lançamento ou a uma conta de A (422 no corpo, com a mesma mensagem da
 * inexistente). Os pares de A não trazem lançamentos de B.
 */
class IsolamentoDaConversaoIT extends ConversaoBase {

    @Test
    void outroUsuarioNaoVeNaoConverteENaoDesfazNadaDeA() {
        UsuarioDeTeste dono = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long horizonte = novaConta(dono, "Banco Horizonte");
        long conjunta = novaConta(dono, "Conta conjunta");
        long ted = importado(dono, horizonte, "2026-11-02", "-500.00", "TED DO DONO");
        long recebida = importado(dono, conjunta, "2026-11-02", "500.00", "RECEBIDA DO DONO");
        long transferenciaDoDono = converterComPar(dono, ted, recebida, null);
        long saqueDoDono = importado(dono, horizonte, "2026-11-04", "-300.00", "SAQUE DO DONO");
        long pixDoDono = importado(dono, horizonte, "2026-11-05", "-700.00", "PIX DO DONO");
        long reservaDoDono = manual(dono, conjunta, "RECEITA", "300", "2026-11-04", "Reserva do dono");
        long contaDoOutro = novaConta(outro, "Do outro");
        long poupancaDoOutro = novaConta(outro, "Poupança");
        long tedDoOutro = importado(outro, contaDoOutro, "2026-11-04", "-300.00", "TED DO OUTRO");
        manual(outro, poupancaDoOutro, "RECEITA", "700", "2026-11-05", "Igual ao pix do dono");

        assertThat(api.get(outro, "/api/lancamentos/{id}/pares", saqueDoDono)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Lançamento não encontrado.");
        assertThat(converter(outro, saqueDoDono, null, poupancaDoOutro, null)).hasStatus(404);
        assertThat(converter(outro, tedDoOutro, reservaDoDono, null, null)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("O outro lado precisa ter o mesmo valor, o sinal oposto e estar em outra conta, com até 5 dias de diferença.");
        assertThat(converter(outro, tedDoOutro, null, conjunta, null)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Conta não encontrada.");
        assertThat(api.acao(outro, "/api/transferencias/{id}/desfazer", transferenciaDoDono)).hasStatus(404).bodyJson()
                .extractingPath("detail").isEqualTo("Transferência não encontrada.");
        assertThat(api.put(outro, "/api/transferencias/{id}", transferencia("500", "2026-11-02", contaDoOutro, poupancaDoOutro, null),
                transferenciaDoDono)).hasStatus(404);
        assertThat(api.delete(outro, "/api/transferencias/{id}", transferenciaDoDono)).hasStatus(404);
        assertThat(texto(api.get(outro, "/api/importacoes"))).doesNotContain("Conta conjunta", "Banco Horizonte");

        assertThat(api.get(dono, "/api/lancamentos/{id}/pares", pixDoDono)).bodyJson().extractingPath("pares").asArray().isEmpty();
        assertThat(lados(transferenciaDoDono)).hasSize(2);
        assertThat(lancamentoNoBanco(saqueDoDono)).isEqualTo("DESPESA -300.00 2026-11-04 SAQUE DO DONO sem solto");
        assertThat(lancamentoNoBanco(reservaDoDono)).isEqualTo("RECEITA 300.00 2026-11-04 Reserva do dono sem solto");
        assertThat(lancamentoNoBanco(tedDoOutro)).isEqualTo("DESPESA -300.00 2026-11-04 TED DO OUTRO sem solto");
        assertThat(contar("transferencias", outro)).isZero();
    }
}