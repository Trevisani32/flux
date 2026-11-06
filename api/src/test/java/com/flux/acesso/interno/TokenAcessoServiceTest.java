package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenAcessoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T15:00:00Z");

    @Test
    void emiteJwtHs256ComClaimsEsperadas() {
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        PropriedadesAcesso propriedades = propriedades(Duration.ofMinutes(15));
        SegurancaConfig config = new SegurancaConfig();
        TokenAcessoService service = new TokenAcessoService(config.jwtEncoder(propriedades), clock, propriedades);

        TokenAcessoEmitido emitido = service.emitir(42L);
        JwtDecoder decoder = config.jwtDecoder(propriedades, clock);
        Jwt jwt = decoder.decode(emitido.valor());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getIssuedAt()).isEqualTo(AGORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(emitido.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(15)));
    }

    @Test
    void rejeitaJwtExpiradoSemTolerancia() {
        Clock emissao = Clock.fixed(AGORA, ZoneOffset.UTC);
        PropriedadesAcesso propriedades = propriedades(Duration.ofMinutes(15));
        SegurancaConfig config = new SegurancaConfig();
        TokenAcessoService service = new TokenAcessoService(config.jwtEncoder(propriedades), emissao, propriedades);
        TokenAcessoEmitido emitido = service.emitir(1L);

        JwtDecoder decoder = config.jwtDecoder(propriedades,
                Clock.fixed(AGORA.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> decoder.decode(emitido.valor()))
                .isInstanceOf(JwtValidationException.class);
    }

    private PropriedadesAcesso propriedades(Duration validadeAcesso) {
        return new PropriedadesAcesso(
                new PropriedadesAcesso.PropriedadesJwt("segredo-de-teste-com-mais-de-trinta-e-dois-bytes", validadeAcesso),
                new PropriedadesAcesso.PropriedadesRenovacao(Duration.ofDays(30), true),
                new PropriedadesAcesso.PropriedadesUsuarioInicial("", "", ""));
    }
}
