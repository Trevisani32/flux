package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.flux.compartilhado.email.EnvioDeEmail;
import com.flux.compartilhado.email.MensagemDeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Envia o link por e-mail depois do commit, numa transação nova. Gera o valor do link
 * aqui, grava só o hash e manda o e-mail. Se o envio falhar, a transação desfaz o hash
 * e a publicação fica como falha no registro de eventos, para ser reenviada.
 */
@Component
public class EnvioDeLinksPorEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvioDeLinksPorEmail.class);

    private final TokenEmailRepository tokenEmailRepository;
    private final UsuarioRepository usuarioRepository;
    private final ModelosDeEmail modelos;
    private final EnvioDeEmail envioDeEmail;
    private final Clock clock;

    public EnvioDeLinksPorEmail(TokenEmailRepository tokenEmailRepository, UsuarioRepository usuarioRepository,
                                ModelosDeEmail modelos, EnvioDeEmail envioDeEmail, Clock clock) {
        this.tokenEmailRepository = tokenEmailRepository;
        this.usuarioRepository = usuarioRepository;
        this.modelos = modelos;
        this.envioDeEmail = envioDeEmail;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void enviar(LinkPorEmailSolicitado evento) {
        Instant agora = Instant.now(clock);
        Optional<TokenEmail> token = tokenEmailRepository.buscarComTravaPorId(evento.tokenId())
                .filter(encontrado -> encontrado.pendente(agora));
        Optional<Usuario> usuario = token.flatMap(encontrado -> usuarioRepository.findById(encontrado.getUsuarioId()));
        if (token.isEmpty() || usuario.isEmpty()) {
            LOGGER.info("Link por e-mail {} não enviado: o link não está mais pendente", evento.tokenId());
            return;
        }
        String valor = Tokens.novoValor();
        token.get().definirHash(Tokens.hash(valor));
        envioDeEmail.enviar(mensagem(token.get(), usuario.get(), valor));
    }

    private MensagemDeEmail mensagem(TokenEmail token, Usuario usuario, String valor) {
        return switch (token.getFinalidade()) {
            case CONFIRMACAO -> modelos.confirmacaoDeCadastro(usuario.getEmail(), usuario.getNome(), valor);
            case REDEFINICAO -> modelos.redefinicaoDeSenha(usuario.getEmail(), usuario.getNome(), valor);
            case TROCA_DE_EMAIL -> modelos.confirmacaoDeNovoEmail(token.getEmailNovo(), usuario.getNome(), valor);
        };
    }
}
