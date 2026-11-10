package com.flux.acesso.interno;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.flux.acesso.UsuarioCriado;
import com.flux.acesso.interno.dto.ContaResponse;
import com.flux.acesso.interno.dto.EntrarRequest;
import com.flux.acesso.interno.dto.UsuarioResponse;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRenovacaoRepository tokenRenovacaoRepository;
    private final TokenEmailRepository tokenEmailRepository;
    private final TokensDeEmail tokensDeEmail;
    private final LimitesDaConta limites;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Validator validator;

    public UsuarioService(UsuarioRepository usuarioRepository, TokenRenovacaoRepository tokenRenovacaoRepository,
                          TokenEmailRepository tokenEmailRepository, TokensDeEmail tokensDeEmail,
                          LimitesDaConta limites, PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher eventos, Clock clock, Validator validator) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRenovacaoRepository = tokenRenovacaoRepository;
        this.tokenEmailRepository = tokenEmailRepository;
        this.tokensDeEmail = tokensDeEmail;
        this.limites = limites;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.clock = clock;
        this.validator = validator;
    }

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Cria um usuário já confirmado (usuário inicial e testes). O cadastro público é o CadastroService. */
    @Transactional
    public Usuario criar(String nome, String email, String senha) {
        String emailNormalizado = normalizarEmail(email);
        if (!validator.validateValue(EntrarRequest.class, "email", emailNormalizado).isEmpty()) {
            throw new RegraDeNegocioException("E-mail inválido.");
        }

        String nomeNormalizado = nome == null ? "" : nome.strip();
        if (nomeNormalizado.isBlank()) {
            nomeNormalizado = "Usuário";
        }
        if (nomeNormalizado.length() > 120) {
            throw new RegraDeNegocioException("O nome pode ter no máximo 120 caracteres.");
        }

        if (senha == null || senha.length() < 10 || senha.length() > 72) {
            throw new RegraDeNegocioException("A senha precisa ter entre 10 e 72 caracteres.");
        }
        if (senha.getBytes(UTF_8).length > 72) {
            throw new RegraDeNegocioException("A senha é longa demais.");
        }

        Instant agora = Instant.now(clock);
        Usuario usuario = new Usuario(nomeNormalizado, emailNormalizado, passwordEncoder.encode(senha), agora);
        usuario.confirmarEmail(agora);
        usuarioRepository.save(usuario);
        // Os ouvintes síncronos (categorias padrão) gravam nesta mesma transação.
        eventos.publishEvent(new UsuarioCriado(usuario.getId()));
        return usuario;
    }

    public boolean existeEmail(String email) {
        return usuarioRepository.existsByEmail(normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public ContaResponse conta(Long usuarioId) {
        return contaDe(buscar(usuarioId));
    }

    @Transactional
    public ContaResponse renomear(Long usuarioId, String nome) {
        Usuario usuario = buscar(usuarioId);
        usuario.renomear(nome, Instant.now(clock));
        return contaDe(usuario);
    }

    @Transactional
    public ContaResponse pedirTrocaDeEmail(Long usuarioId, String novoEmail, String senhaAtual) {
        String emailNovo = normalizarEmail(novoEmail);
        // Os limites vêm antes da senha, para um 429 não revelar se ela estava certa.
        limites.pedirTrocaDeEmail(usuarioId, emailNovo);
        Usuario usuario = usuarioRepository.buscarComTravaPorId(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            limites.devolverTrocaDeEmail(usuarioId, emailNovo);
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }
        if (emailNovo.equals(usuario.getEmail())) {
            limites.devolverTrocaDeEmail(usuarioId, emailNovo);
            throw new RegraDeNegocioException("O novo e-mail é igual ao atual.");
        }
        if (usuarioRepository.existsByEmail(emailNovo)) {
            // E-mail de outra conta: a resposta e a pendência são as mesmas do caso normal, para a
            // troca não revelar quais e-mails têm conta. Só que o link nunca sai, e nada vai para lá.
            tokensDeEmail.criarSemEnvio(usuarioId, FinalidadeToken.TROCA_DE_EMAIL, emailNovo);
        } else {
            tokensDeEmail.criar(usuarioId, FinalidadeToken.TROCA_DE_EMAIL, emailNovo);
        }
        eventos.publishEvent(new TrocaDeEmailPedida(UUID.randomUUID(), usuario.getEmail(), usuario.getNome(),
                emailNovo));
        return contaDe(usuario);
    }

    @Transactional
    public void trocarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        limites.conferirSenhaAtual(usuarioId);
        Usuario usuario = buscar(usuarioId);
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }
        if (novaSenha.getBytes(UTF_8).length > 72) {
            throw new RegraDeNegocioException("A nova senha é longa demais.");
        }
        Instant agora = Instant.now(clock);
        usuario.trocarSenha(passwordEncoder.encode(novaSenha), agora);
        tokenRenovacaoRepository.revogarTodosDoUsuario(usuarioId, agora);
    }

    @Transactional
    public void excluir(Long usuarioId, String senhaAtual) {
        limites.conferirSenhaAtual(usuarioId);
        Usuario usuario = buscar(usuarioId);
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }
        eventos.publishEvent(new ContaExcluida(UUID.randomUUID(), usuario.getEmail(), usuario.getNome(),
                Instant.now(clock)));
        // ON DELETE CASCADE apaga sessões, links por e-mail e, nas próximas fatias, todos os dados do usuário.
        usuarioRepository.delete(usuario);
    }

    public UsuarioResponse resposta(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }

    private Usuario buscar(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }

    private ContaResponse contaDe(Usuario usuario) {
        ContaResponse.TrocaDeEmailPendente pendente = tokenEmailRepository
                .pendentes(usuario.getId(), FinalidadeToken.TROCA_DE_EMAIL, Instant.now(clock)).stream()
                .findFirst()
                .map(token -> new ContaResponse.TrocaDeEmailPendente(token.getEmailNovo(), token.getExpiraEm()))
                .orElse(null);
        ContaResponse.TermosAceitos termos = usuario.getTermosVersao() == null
                ? null
                : new ContaResponse.TermosAceitos(usuario.getTermosVersao(), usuario.getTermosAceitosEm());
        return new ContaResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), pendente, termos);
    }
}
