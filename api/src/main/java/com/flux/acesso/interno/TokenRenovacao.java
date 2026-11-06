package com.flux.acesso.interno;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tokens_renovacao")
public class TokenRenovacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "revogado_em")
    private Instant revogadoEm;

    @Column(name = "substituido_por")
    private Long substituidoPor;

    public TokenRenovacao() {
    }

    public TokenRenovacao(Long usuarioId, String tokenHash, Instant expiraEm, Instant criadoEm) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEm = expiraEm;
        this.criadoEm = criadoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getRevogadoEm() {
        return revogadoEm;
    }

    public Long getSubstituidoPor() {
        return substituidoPor;
    }

    public void rotacionar(Instant agora, Long novoId) {
        this.revogadoEm = agora;
        this.substituidoPor = novoId;
    }

    public void revogar(Instant agora) {
        this.revogadoEm = agora;
    }
}
