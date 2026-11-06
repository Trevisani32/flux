package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.flux.acesso.interno.dto.SessaoResponse;
import com.flux.acesso.interno.dto.UsuarioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticacaoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T15:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRenovacaoRepository tokenRenovacaoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAcessoService tokenAcessoService;

    @Mock
    private UsuarioService usuarioService;

    private AutenticacaoService service;

    @BeforeEach
    void prepara() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-ficticio");
        service = new AutenticacaoService(usuarioRepository, tokenRenovacaoRepository, passwordEncoder,
                tokenAcessoService, usuarioService, Clock.fixed(AGORA, ZoneOffset.UTC), propriedades());
    }

    @Test
    void usaHashFicticioParaEmailInexistente() {
        when(usuarioRepository.findByEmail("ausente@teste.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("ausente@teste.local", "senha"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(passwordEncoder).matches(eq("senha"), anyString());
    }

    @Test
    void normalizaEmailNoLogin() {
        when(usuarioRepository.findByEmail("dev@exemplo.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entrar("  DEV@EXEMPLO.LOCAL ", "senha"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository).findByEmail("dev@exemplo.local");
    }

    @Test
    void rotacionaTokenComTrava() {
        TokenRenovacao antigo = spy(new TokenRenovacao(1L, "hash", AGORA.plus(Duration.ofDays(1)), AGORA));
        TokenRenovacao novo = org.mockito.Mockito.mock(TokenRenovacao.class);
        when(novo.getId()).thenReturn(2L);
        Usuario usuario = usuario(1L);
        when(tokenRenovacaoRepository.buscarComTravaPorHash(anyString())).thenReturn(Optional.of(antigo));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(tokenRenovacaoRepository.save(any(TokenRenovacao.class))).thenReturn(novo);
        when(tokenAcessoService.emitir(1L)).thenReturn(new TokenAcessoEmitido("jwt", AGORA.plus(Duration.ofMinutes(15))));
        when(usuarioService.resposta(usuario)).thenReturn(new UsuarioResponse(1L, "Teste", "teste@teste.local"));

        service.renovar("token");

        verify(tokenRenovacaoRepository).buscarComTravaPorHash(AutenticacaoService.hash("token"));
        verify(tokenRenovacaoRepository, never()).findByTokenHash(anyString());
        verify(antigo).rotacionar(AGORA, 2L);
    }

    @Test
    void reusoRevogaTodasAsSessoes() {
        TokenRenovacao antigo = new TokenRenovacao(1L, "hash", AGORA.plus(Duration.ofDays(1)), AGORA);
        antigo.rotacionar(AGORA.minusSeconds(1), 2L);
        when(tokenRenovacaoRepository.buscarComTravaPorHash(anyString())).thenReturn(Optional.of(antigo));

        assertThatThrownBy(() -> service.renovar("token"))
                .isInstanceOf(SessaoInvalidaException.class);

        verify(tokenRenovacaoRepository).buscarComTravaPorHash(AutenticacaoService.hash("token"));
        verify(tokenRenovacaoRepository, never()).findByTokenHash(anyString());
        verify(tokenRenovacaoRepository).revogarTodosDoUsuario(1L, AGORA);
    }

    @Test
    void tokenRevogadoSemSubstitutoNaoRevogaTodos() {
        TokenRenovacao token = new TokenRenovacao(1L, "hash", AGORA.plus(Duration.ofDays(1)), AGORA);
        token.revogar(AGORA.minusSeconds(1));
        when(tokenRenovacaoRepository.buscarComTravaPorHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.renovar("token"))
                .isInstanceOf(SessaoInvalidaException.class);

        verify(tokenRenovacaoRepository, never()).revogarTodosDoUsuario(any(), any());
    }

    @Test
    void tokenExpiradoAusenteOuInexistenteEInvalido() {
        TokenRenovacao expirado = new TokenRenovacao(1L, "hash", AGORA, AGORA);
        when(tokenRenovacaoRepository.buscarComTravaPorHash(anyString())).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> service.renovar("token"))
                .isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> service.renovar(null))
                .isInstanceOf(SessaoInvalidaException.class);
        when(tokenRenovacaoRepository.buscarComTravaPorHash(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.renovar("inexistente"))
                .isInstanceOf(SessaoInvalidaException.class);
    }

    private PropriedadesAcesso propriedades() {
        return new PropriedadesAcesso(
                new PropriedadesAcesso.PropriedadesJwt("segredo-de-teste-com-mais-de-trinta-e-dois-bytes", Duration.ofMinutes(15)),
                new PropriedadesAcesso.PropriedadesRenovacao(Duration.ofDays(30), true),
                new PropriedadesAcesso.PropriedadesUsuarioInicial("", "", ""));
    }

    private Usuario usuario(Long id) {
        Usuario usuario = org.mockito.Mockito.mock(Usuario.class);
        when(usuario.getId()).thenReturn(id);
        return usuario;
    }

}
