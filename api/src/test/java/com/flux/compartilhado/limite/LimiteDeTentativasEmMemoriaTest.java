package com.flux.compartilhado.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteDeTentativasEmMemoriaTest {

    @Test
    void calculaEsperaESegundosArredondadosNaQuartaTentativa() {
        RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-11-07T12:00:00Z"));
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(relogio);
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            limite.consumir("teste", 3, Duration.ofHours(1));
            relogio.avancar(Duration.ofSeconds(10));
        }

        assertThatThrownBy(() -> limite.consumir("teste", 3, Duration.ofHours(1)))
                .isInstanceOfSatisfying(LimiteExcedidoException.class, excecao -> {
                    assertThat(excecao.getEspera()).isEqualTo(Duration.ofMinutes(59).plusSeconds(30));
                    assertThat(excecao.segundosParaTentarDeNovo()).isEqualTo(3570);
                });
    }

    @Test
    void arredondaMeioSegundoDeEsperaParaUmSegundo() {
        RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-11-07T12:00:00Z"));
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(relogio);
        limite.consumir("teste", 1, Duration.ofHours(1));
        relogio.avancar(Duration.ofMinutes(59).plusSeconds(59).plusMillis(500));

        assertThatThrownBy(() -> limite.consumir("teste", 1, Duration.ofHours(1)))
                .isInstanceOfSatisfying(LimiteExcedidoException.class,
                        excecao -> assertThat(excecao.segundosParaTentarDeNovo()).isEqualTo(1));
    }

    @Test
    void aceitaNaBordaExataDaJanela() {
        RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-11-07T12:00:00Z"));
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(relogio);
        limite.consumir("teste", 1, Duration.ofHours(1));
        relogio.avancar(Duration.ofHours(1));

        limite.consumir("teste", 1, Duration.ofHours(1));
    }

    @Test
    void devolverLiberaUmaTentativa() {
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(
                Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));
        limite.consumir("teste", 1, Duration.ofHours(1));
        limite.devolver("teste");

        limite.consumir("teste", 1, Duration.ofHours(1));
    }

    @Test
    void devolverChaveInexistenteNaoCriaJanela() {
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(
                Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));
        limite.devolver("inexistente");

        assertThat(limite.quantidadeDeChaves()).isZero();
    }

    @Test
    void limpezaRemoveJanelasVencidas() {
        RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-11-07T12:00:00Z"));
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(relogio);
        limite.consumir("teste", 1, Duration.ofHours(1));
        relogio.avancar(Duration.ofHours(2));

        limite.limparVencidos();

        assertThat(limite.quantidadeDeChaves()).isZero();
    }

    @Test
    void concorrenciaAceitaExatamenteOMaximo() throws Exception {
        LimiteDeTentativasEmMemoria limite = new LimiteDeTentativasEmMemoria(
                Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));
        try (var executor = Executors.newFixedThreadPool(16)) {
            var tarefas = java.util.stream.IntStream.range(0, 200)
                    .<Callable<Boolean>>mapToObj(ignorado -> () -> {
                        try {
                            limite.consumir("concorrente", 10, Duration.ofHours(1));
                            return true;
                        } catch (LimiteExcedidoException ex) {
                            return false;
                        }
                    }).toList();

            long aceitas = executor.invokeAll(tarefas).stream().filter(futuro -> {
                try {
                    return futuro.get();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }).count();

            assertThat(aceitas).isEqualTo(10);
        }
    }

    private static final class RelogioMutavel extends Clock {

        private Instant agora;

        private RelogioMutavel(Instant agora) {
            this.agora = agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }

        private void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }
    }
}
