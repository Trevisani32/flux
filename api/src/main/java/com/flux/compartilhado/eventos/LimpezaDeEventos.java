package com.flux.compartilhado.eventos;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga, todo dia, as publicações de evento com mais de 7 dias. Com o modo de
 * conclusão DELETE, toda linha que sobra é de um evento que nunca deu certo.
 */
@Component
public class LimpezaDeEventos {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimpezaDeEventos.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public LimpezaDeEventos(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    public void apagarAntigas() {
        Instant limite = Instant.now(clock).minus(Duration.ofDays(7));
        int apagadas = jdbcTemplate.update("delete from event_publication where publication_date < ?",
                Timestamp.from(limite));
        if (apagadas > 0) {
            LOGGER.warn("{} publicações de evento com mais de 7 dias foram apagadas sem conclusão", apagadas);
        }
    }
}
