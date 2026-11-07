package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Todo dia às 3h (São Paulo): cadastros não confirmados há 7 dias, links e sessões vencidos. */
@Component
public class LimpezaDiaria {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimpezaDiaria.class);

    private final UsuarioRepository usuarioRepository;
    private final TokenEmailRepository tokenEmailRepository;
    private final TokenRenovacaoRepository tokenRenovacaoRepository;
    private final Clock clock;

    public LimpezaDiaria(UsuarioRepository usuarioRepository, TokenEmailRepository tokenEmailRepository,
                         TokenRenovacaoRepository tokenRenovacaoRepository, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tokenEmailRepository = tokenEmailRepository;
        this.tokenRenovacaoRepository = tokenRenovacaoRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void executar() {
        Instant agora = Instant.now(clock);
        int cadastros = usuarioRepository.apagarNaoConfirmadosCriadosAntesDe(agora.minus(Duration.ofDays(7)));
        int links = tokenEmailRepository.apagarVencidos(agora);
        int sessoes = tokenRenovacaoRepository.apagarVencidos(agora);
        LOGGER.info("Limpeza diária: {} cadastros não confirmados, {} links por e-mail e {} sessões vencidas apagados",
                cadastros, links, sessoes);
    }
}
