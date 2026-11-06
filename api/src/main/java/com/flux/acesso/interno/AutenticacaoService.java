package com.flux.acesso.interno;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.flux.acesso.interno.dto.SessaoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacaoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutenticacaoService.class);

    private final UsuarioRepository usuarioRepository;
    private final TokenRenovacaoRepository tokenRenovacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenAcessoService tokenAcessoService;
    private final UsuarioService usuarioService;
    private final Clock clock;
    private final PropriedadesAcesso propriedades;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String hashFicticio;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               TokenRenovacaoRepository tokenRenovacaoRepository,
                               PasswordEncoder passwordEncoder,
                               TokenAcessoService tokenAcessoService,
                               UsuarioService usuarioService, Clock clock,
                               PropriedadesAcesso propriedades) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRenovacaoRepository = tokenRenovacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenAcessoService = tokenAcessoService;
        this.usuarioService = usuarioService;
        this.clock = clock;
        this.propriedades = propriedades;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public ResultadoAutenticacao entrar(String email, String senha) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(UsuarioService.normalizarEmail(email));
        if (usuario.isEmpty()) {
            passwordEncoder.matches(senha, hashFicticio);
            throw new CredenciaisInvalidasException();
        }
        if (!passwordEncoder.matches(senha, usuario.get().getSenhaHash())) {
            throw new CredenciaisInvalidasException();
        }
        return criarSessao(usuario.get());
    }

    @Transactional(noRollbackFor = SessaoInvalidaException.class)
    public ResultadoAutenticacao renovar(String tokenRenovacao) {
        if (tokenRenovacao == null || tokenRenovacao.isBlank()) {
            throw new SessaoInvalidaException();
        }

        String hash = hash(tokenRenovacao);
        TokenRenovacao token = tokenRenovacaoRepository.buscarComTravaPorHash(hash)
                .orElseThrow(SessaoInvalidaException::new);
        Instant agora = Instant.now(clock);
        if (token.getSubstituidoPor() != null) {
            tokenRenovacaoRepository.revogarTodosDoUsuario(token.getUsuarioId(), agora);
            LOGGER.warn("Reuso de token de renovação detectado; sessões do usuário {} revogadas", token.getUsuarioId());
            throw new SessaoInvalidaException();
        }
        if (token.getRevogadoEm() != null || !token.getExpiraEm().isAfter(agora)) {
            throw new SessaoInvalidaException();
        }

        Usuario usuario = usuarioRepository.findById(token.getUsuarioId())
                .orElseThrow(SessaoInvalidaException::new);
        String novoValor = novoToken();
        TokenRenovacao novoToken = tokenRenovacaoRepository.save(new TokenRenovacao(
                usuario.getId(), hash(novoValor), agora.plus(propriedades.renovacao().validade()), agora));
        token.rotacionar(agora, novoToken.getId());
        TokenAcessoEmitido tokenAcesso = tokenAcessoService.emitir(usuario.getId());
        SessaoResponse sessao = new SessaoResponse(
                tokenAcesso.valor(), tokenAcesso.expiraEm(), usuarioService.resposta(usuario));
        return new ResultadoAutenticacao(sessao, novoValor);
    }

    @Transactional
    public void sair(String tokenRenovacao) {
        if (tokenRenovacao == null || tokenRenovacao.isBlank()) {
            return;
        }
        tokenRenovacaoRepository.findByTokenHash(hash(tokenRenovacao))
                .filter(token -> token.getRevogadoEm() == null)
                .ifPresent(token -> token.revogar(Instant.now(clock)));
    }

    private ResultadoAutenticacao criarSessao(Usuario usuario) {
        Instant agora = Instant.now(clock);
        String tokenRenovacao = novoToken();
        tokenRenovacaoRepository.save(new TokenRenovacao(
                usuario.getId(), hash(tokenRenovacao), agora.plus(propriedades.renovacao().validade()), agora));
        TokenAcessoEmitido tokenAcesso = tokenAcessoService.emitir(usuario.getId());
        SessaoResponse sessao = new SessaoResponse(
                tokenAcesso.valor(), tokenAcesso.expiraEm(), usuarioService.resposta(usuario));
        return new ResultadoAutenticacao(sessao, tokenRenovacao);
    }

    private String novoToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
