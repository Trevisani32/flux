package com.flux.acesso.interno;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.flux.acesso.UsuarioCriado;
import com.flux.acesso.interno.dto.ConfirmacaoDeEmailResponse;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro público, confirmação de e-mail, reenvio, "esqueci a senha" e redefinição. */
@Service
public class CadastroService {

    private final UsuarioRepository usuarioRepository;
    private final TokenEmailRepository tokenEmailRepository;
    private final TokenRenovacaoRepository tokenRenovacaoRepository;
    private final TokensDeEmail tokensDeEmail;
    private final LimitesDaConta limites;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public CadastroService(UsuarioRepository usuarioRepository, TokenEmailRepository tokenEmailRepository,
                           TokenRenovacaoRepository tokenRenovacaoRepository, TokensDeEmail tokensDeEmail,
                           LimitesDaConta limites, PasswordEncoder passwordEncoder,
                           ApplicationEventPublisher eventos, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tokenEmailRepository = tokenEmailRepository;
        this.tokenRenovacaoRepository = tokenRenovacaoRepository;
        this.tokensDeEmail = tokensDeEmail;
        this.limites = limites;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional
    public void cadastrar(String nome, String email, String senha, String ip) {
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        limites.cadastrar(ip, emailNormalizado);
        if (senha.getBytes(UTF_8).length > 72) {
            throw new RegraDeNegocioException("A senha é longa demais.");
        }
        // O BCrypt roda sempre, para o tempo de resposta não revelar se o e-mail já existe.
        String senhaHash = passwordEncoder.encode(senha);
        usuarioRepository.travarEmail(emailNormalizado);
        Optional<Usuario> existente = usuarioRepository.findByEmail(emailNormalizado);
        if (existente.isPresent()) {
            if (existente.get().emailConfirmado()) {
                eventos.publishEvent(new TentativaDeCadastroComEmailExistente(UUID.randomUUID(), existente.get().getId()));
            } else {
                // Conta ainda não confirmada: vai um link de confirmação novo, como no reenvio.
                // Nome, senha e aceite continuam os do primeiro cadastro; os deste pedido são descartados.
                tokensDeEmail.criar(existente.get().getId(), FinalidadeToken.CONFIRMACAO, null);
            }
            return;
        }
        Instant agora = Instant.now(clock);
        Usuario usuario = new Usuario(nome, emailNormalizado, senhaHash, agora);
        usuario.aceitarTermos(Termos.VERSAO, agora);
        usuarioRepository.save(usuario);
        // Os ouvintes síncronos (categorias padrão) gravam nesta mesma transação.
        eventos.publishEvent(new UsuarioCriado(usuario.getId()));
        tokensDeEmail.criar(usuario.getId(), FinalidadeToken.CONFIRMACAO, null);
    }

    @Transactional
    public ConfirmacaoDeEmailResponse confirmarEmail(String valor) {
        Instant agora = Instant.now(clock);
        String hash = Tokens.hash(valor);
        // Trava o usuário antes do token, na mesma ordem do TokensDeEmail: a ordem oposta
        // daria deadlock com um pedido de link novo do mesmo usuário.
        Usuario usuario = usuarioRepository.buscarComTravaPorId(
                        tokenEmailRepository.usuarioDoToken(hash).orElseThrow(LinkInvalidoException::new))
                .orElseThrow(LinkInvalidoException::new);
        TokenEmail token = tokenEmailRepository.buscarComTravaPorHash(hash)
                .filter(encontrado -> encontrado.getFinalidade() != FinalidadeToken.REDEFINICAO)
                .filter(encontrado -> encontrado.pendente(agora))
                .orElseThrow(LinkInvalidoException::new);
        String tipo;
        if (token.getFinalidade() == FinalidadeToken.TROCA_DE_EMAIL) {
            usuarioRepository.travarEmail(token.getEmailNovo());
            if (usuarioRepository.existsByEmail(token.getEmailNovo())) {
                throw new RegraDeNegocioException("Este e-mail já está em uso por outra conta.");
            }
            usuario.trocarEmail(token.getEmailNovo(), agora);
            tipo = "TROCA_DE_EMAIL";
        } else {
            usuario.confirmarEmail(agora);
            tipo = "CADASTRO";
        }
        tokenEmailRepository.invalidarOutrosPendentes(usuario.getId(), token.getFinalidade(), token.getId(), agora);
        token.usar(agora);
        return new ConfirmacaoDeEmailResponse(tipo, usuario.getEmail());
    }

    @Transactional
    public void reenviarConfirmacao(String email, String ip) {
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        limites.reenviarConfirmacao(ip, emailNormalizado);
        usuarioRepository.findByEmail(emailNormalizado)
                .filter(usuario -> !usuario.emailConfirmado())
                .ifPresent(usuario -> tokensDeEmail.criar(usuario.getId(), FinalidadeToken.CONFIRMACAO, null));
    }

    @Transactional
    public void esqueciASenha(String email, String ip) {
        String emailNormalizado = UsuarioService.normalizarEmail(email);
        limites.esqueciASenha(ip, emailNormalizado);
        usuarioRepository.findByEmail(emailNormalizado)
                .ifPresent(usuario -> tokensDeEmail.criar(usuario.getId(), FinalidadeToken.REDEFINICAO, null));
    }

    @Transactional
    public void redefinirSenha(String valor, String novaSenha, String ip) {
        limites.redefinirSenha(ip);
        Instant agora = Instant.now(clock);
        String hash = Tokens.hash(valor);
        // Usuário antes do token, como no confirmarEmail.
        Usuario usuario = usuarioRepository.buscarComTravaPorId(
                        tokenEmailRepository.usuarioDoToken(hash).orElseThrow(LinkInvalidoException::new))
                .orElseThrow(LinkInvalidoException::new);
        TokenEmail token = tokenEmailRepository.buscarComTravaPorHash(hash)
                .filter(encontrado -> encontrado.getFinalidade() == FinalidadeToken.REDEFINICAO)
                .filter(encontrado -> encontrado.pendente(agora))
                .orElseThrow(LinkInvalidoException::new);
        if (novaSenha.getBytes(UTF_8).length > 72) {
            throw new RegraDeNegocioException("A nova senha é longa demais.");
        }
        usuario.trocarSenha(passwordEncoder.encode(novaSenha), agora);
        // O link chegou neste endereço: isso também confirma o e-mail.
        usuario.confirmarEmail(agora);
        tokenEmailRepository.invalidarOutrosPendentes(usuario.getId(), FinalidadeToken.REDEFINICAO, token.getId(),
                agora);
        tokenEmailRepository.invalidarPendentes(usuario.getId(), FinalidadeToken.TROCA_DE_EMAIL, agora);
        tokenEmailRepository.invalidarPendentes(usuario.getId(), FinalidadeToken.CONFIRMACAO, agora);
        token.usar(agora);
        tokenRenovacaoRepository.revogarTodosDoUsuario(usuario.getId(), agora);
    }
}
