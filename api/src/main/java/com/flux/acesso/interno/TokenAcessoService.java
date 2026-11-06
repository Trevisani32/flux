package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenAcessoService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final PropriedadesAcesso propriedades;

    public TokenAcessoService(JwtEncoder jwtEncoder, Clock clock, PropriedadesAcesso propriedades) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.propriedades = propriedades;
    }

    public TokenAcessoEmitido emitir(Long usuarioId) {
        Instant agora = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEm = agora.plus(propriedades.jwt().validadeAcesso());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuarioId.toString())
                .issuedAt(agora)
                .expiresAt(expiraEm)
                .build();
        String valor = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenAcessoEmitido(valor, expiraEm);
    }
}
