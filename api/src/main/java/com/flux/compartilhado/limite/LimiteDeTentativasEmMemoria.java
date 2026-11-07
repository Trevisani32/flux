package com.flux.compartilhado.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LimiteDeTentativasEmMemoria implements LimiteDeTentativas {

    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimiteDeTentativasEmMemoria(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void consumir(String chave, int maximo, Duration janela) {
        Instant agora = Instant.now(clock);
        AtomicReference<Duration> espera = new AtomicReference<>();
        janelas.compute(chave, (k, atual) -> {
            Janela registro = atual == null ? new Janela(janela) : atual;
            registro.podar(agora);
            if (registro.instantes.size() >= maximo) {
                espera.set(Duration.between(agora, registro.instantes.peekFirst().plus(registro.duracao)));
            } else {
                registro.instantes.addLast(agora);
            }
            return registro.instantes.isEmpty() ? null : registro;
        });
        if (espera.get() != null) {
            throw new LimiteExcedidoException(espera.get());
        }
    }

    @Override
    public void devolver(String chave) {
        janelas.computeIfPresent(chave, (k, registro) -> {
            registro.instantes.pollLast();
            return registro.instantes.isEmpty() ? null : registro;
        });
    }

    /** Remove o que já saiu da janela, para a memória não guardar contadores vencidos. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void limparVencidos() {
        Instant agora = Instant.now(clock);
        janelas.keySet().forEach(chave -> janelas.computeIfPresent(chave, (k, registro) -> {
            registro.podar(agora);
            return registro.instantes.isEmpty() ? null : registro;
        }));
    }

    /** Só para testes: esquece todos os contadores. */
    public void limpar() {
        janelas.clear();
    }

    /** Só para testes. */
    public int quantidadeDeChaves() {
        return janelas.size();
    }

    private static final class Janela {

        private final Duration duracao;
        private final ArrayDeque<Instant> instantes = new ArrayDeque<>();

        private Janela(Duration duracao) {
            this.duracao = duracao;
        }

        private void podar(Instant agora) {
            Instant corte = agora.minus(duracao);
            while (!instantes.isEmpty() && !instantes.peekFirst().isAfter(corte)) {
                instantes.pollFirst();
            }
        }
    }
}
