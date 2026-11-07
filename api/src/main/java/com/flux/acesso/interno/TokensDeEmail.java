package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokensDeEmail {

    private final TokenEmailRepository tokenEmailRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public TokensDeEmail(TokenEmailRepository tokenEmailRepository, UsuarioRepository usuarioRepository,
                         ApplicationEventPublisher eventos, Clock clock) {
        this.tokenEmailRepository = tokenEmailRepository;
        this.usuarioRepository = usuarioRepository;
        this.eventos = eventos;
        this.clock = clock;
    }

    /**
     * Invalida os links pendentes da mesma finalidade, cria um novo (ainda sem hash) e
     * publica o evento que manda o e-mail depois do commit. Trava a linha do usuário,
     * para dois pedidos simultâneos não deixarem dois links válidos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TokenEmail criar(Long usuarioId, FinalidadeToken finalidade, String emailNovo) {
        TokenEmail token = registrar(usuarioId, finalidade, emailNovo);
        eventos.publishEvent(new LinkPorEmailSolicitado(token.getId()));
        return token;
    }

    /**
     * Igual ao {@link #criar}, mas sem mandar o e-mail. O link fica sem hash, então ninguém
     * consegue usá-lo, e a limpeza diária o apaga quando vence. Serve para a troca para um
     * e-mail que já tem conta responder igual à troca normal.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TokenEmail criarSemEnvio(Long usuarioId, FinalidadeToken finalidade, String emailNovo) {
        return registrar(usuarioId, finalidade, emailNovo);
    }

    private TokenEmail registrar(Long usuarioId, FinalidadeToken finalidade, String emailNovo) {
        usuarioRepository.buscarComTravaPorId(usuarioId);
        Instant agora = Instant.now(clock);
        tokenEmailRepository.invalidarPendentes(usuarioId, finalidade, agora);
        return tokenEmailRepository.save(new TokenEmail(usuarioId, finalidade, emailNovo, agora));
    }
}
