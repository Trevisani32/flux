package com.flux.suporte;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public class RelogioDeTeste extends Clock {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private Duration deslocamento = Duration.ZERO;

    @Override
    public ZoneId getZone() {
        return FUSO;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.offset(Clock.system(zone), deslocamento);
    }

    @Override
    public synchronized Instant instant() {
        return Instant.now().plus(deslocamento);
    }

    public synchronized void avancar(Duration duracao) {
        deslocamento = deslocamento.plus(duracao);
    }

    public synchronized void reiniciar() {
        deslocamento = Duration.ZERO;
    }
}
