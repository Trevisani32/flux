package com.flux.acesso.interno;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Link de uso único enviado por e-mail. O valor do link nunca é guardado: o envio
 * (EnvioDeLinksPorEmail) gera o valor, grava só o hash e manda o e-mail.
 */
@Entity
@Table(name = "tokens_email")
public class TokenEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FinalidadeToken finalidade;

    @Column(name = "token_hash", unique = true, length = 64)
    private String tokenHash;

    @Column(name = "email_novo", length = 255)
    private String emailNovo;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "usado_em")
    private Instant usadoEm;

    @Column(name = "invalidado_em")
    private Instant invalidadoEm;

    public TokenEmail() {
    }

    public TokenEmail(Long usuarioId, FinalidadeToken finalidade, String emailNovo, Instant agora) {
        this.usuarioId = usuarioId;
        this.finalidade = finalidade;
        this.emailNovo = emailNovo;
        this.criadoEm = agora;
        this.expiraEm = agora.plus(finalidade.validade());
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public FinalidadeToken getFinalidade() {
        return finalidade;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getEmailNovo() {
        return emailNovo;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getUsadoEm() {
        return usadoEm;
    }

    public Instant getInvalidadoEm() {
        return invalidadoEm;
    }

    /** Ainda pode ser usado: não foi usado, não foi invalidado e não venceu. */
    public boolean pendente(Instant agora) {
        return usadoEm == null && invalidadoEm == null && expiraEm.isAfter(agora);
    }

    public void definirHash(String hash) {
        this.tokenHash = hash;
    }

    public void usar(Instant agora) {
        this.usadoEm = agora;
    }
}
