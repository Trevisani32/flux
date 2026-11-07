package com.flux.acesso.interno;

import java.time.Duration;

import com.flux.compartilhado.limite.LimiteDeTentativasEmMemoria;
import com.flux.compartilhado.limite.LimiteExcedidoException;
import com.flux.suporte.RelogioDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitesDaContaTest {

    @Test
    void limitaTrocasPorContaEAceitaDepoisDeUmaHora() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LimitesDaConta limites = limites(relogio);
        for (int indice = 0; indice < 3; indice++) {
            limites.pedirTrocaDeEmail(1L, "novo-" + indice + "@teste.local");
        }

        assertThatThrownBy(() -> limites.pedirTrocaDeEmail(1L, "quarto@teste.local"))
                .isInstanceOfSatisfying(LimiteExcedidoException.class,
                        excecao -> org.assertj.core.api.Assertions.assertThat(excecao.segundosParaTentarDeNovo()).isBetween(1L, 3600L));

        relogio.avancar(Duration.ofHours(1));
        limites.pedirTrocaDeEmail(1L, "depois@teste.local");
    }

    @Test
    void limitaTrocasPorEnderecoSemContarPedidoBarradoNaConta() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LimitesDaConta limites = limites(relogio);
        String destino = "compartilhado@teste.local";
        for (long usuario = 1; usuario <= 3; usuario++) {
            limites.pedirTrocaDeEmail(usuario, destino);
        }

        assertThatThrownBy(() -> limites.pedirTrocaDeEmail(4L, destino)).isInstanceOf(LimiteExcedidoException.class);
        for (int indice = 0; indice < 3; indice++) {
            limites.pedirTrocaDeEmail(4L, "livre-" + indice + "@teste.local");
        }
        assertThatThrownBy(() -> limites.pedirTrocaDeEmail(4L, "livre-quarto@teste.local"))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void devolveTrocaQuandoSenhaAtualFalha() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LimitesDaConta limites = limites(relogio);
        for (int indice = 0; indice < 3; indice++) {
            limites.pedirTrocaDeEmail(1L, "novo-" + indice + "@teste.local");
        }
        assertThatThrownBy(() -> limites.pedirTrocaDeEmail(1L, "barrado@teste.local"))
                .isInstanceOf(LimiteExcedidoException.class);

        for (int indice = 0; indice < 7; indice++) {
            limites.conferirSenhaAtual(1L);
        }
        assertThatThrownBy(() -> limites.conferirSenhaAtual(1L)).isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void devolverTrocaLiberaOsDoisContadores() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LimitesDaConta limites = limites(relogio);
        for (int indice = 0; indice < 5; indice++) {
            limites.pedirTrocaDeEmail(1L, "devolvido-" + indice + "@teste.local");
            limites.devolverTrocaDeEmail(1L, "devolvido-" + indice + "@teste.local");
        }
        for (int indice = 0; indice < 3; indice++) {
            limites.pedirTrocaDeEmail(1L, "aceito-" + indice + "@teste.local");
        }
        assertThatThrownBy(() -> limites.pedirTrocaDeEmail(1L, "quarto@teste.local"))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void limiteDaSenhaAtualBloqueiaTrocaEAposQuinzeMinutosLiberaTres() {
        RelogioDeTeste relogio = new RelogioDeTeste();
        LimitesDaConta limites = limites(relogio);
        for (int indice = 0; indice < 10; indice++) {
            limites.conferirSenhaAtual(1L);
        }
        assertThatThrownBy(() -> limites.pedirTrocaDeEmail(1L, "bloqueado@teste.local"))
                .isInstanceOf(LimiteExcedidoException.class);

        relogio.avancar(Duration.ofMinutes(15));
        for (int indice = 0; indice < 3; indice++) {
            limites.pedirTrocaDeEmail(1L, "liberado-" + indice + "@teste.local");
        }
    }

    private static LimitesDaConta limites(RelogioDeTeste relogio) {
        return new LimitesDaConta(new LimiteDeTentativasEmMemoria(relogio),
                new PropriedadesConta("http://localhost:4200", false, null, true));
    }
}
