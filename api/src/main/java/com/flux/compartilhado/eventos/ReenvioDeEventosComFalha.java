package com.flux.compartilhado.eventos;

import java.time.Duration;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada 5 minutos, reenvia ao listener as publicações de evento que falharam há pelo
 * menos 5 minutos (ex.: SMTP fora do ar). As que estão em andamento não são tocadas.
 */
@Component
public class ReenvioDeEventosComFalha {

    private final FailedEventPublications falhas;

    public ReenvioDeEventosComFalha(FailedEventPublications falhas) {
        this.falhas = falhas;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void reenviar() {
        falhas.resubmit(ResubmissionOptions.defaults()
                .withMinAge(Duration.ofMinutes(5))
                .withBatchSize(50)
                .withMaxInFlight(50));
    }
}
