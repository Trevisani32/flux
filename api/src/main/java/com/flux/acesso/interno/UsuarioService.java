package com.flux.acesso.interno;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import com.flux.acesso.interno.dto.EntrarRequest;
import com.flux.acesso.interno.dto.UsuarioResponse;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import jakarta.validation.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final TokenRenovacaoRepository tokenRenovacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Validator validator;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          TokenRenovacaoRepository tokenRenovacaoRepository,
                          PasswordEncoder passwordEncoder, Clock clock, Validator validator) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRenovacaoRepository = tokenRenovacaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.validator = validator;
    }

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

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
        return usuarioRepository.save(usuario);
    }

    public boolean existeEmail(String email) {
        return usuarioRepository.existsByEmail(normalizarEmail(email));
    }

    @Transactional(readOnly = true)
    public UsuarioResponse dadosDoUsuario(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        return resposta(usuario);
    }

    @Transactional
    public void trocarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
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

    public UsuarioResponse resposta(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
