package com.flux.acesso.interno;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    @Column(name = "termos_versao", length = 10)
    private String termosVersao;

    @Column(name = "termos_aceitos_em")
    private Instant termosAceitosEm;

    public Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash, Instant agora) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getEmailConfirmadoEm() {
        return emailConfirmadoEm;
    }

    public String getTermosVersao() {
        return termosVersao;
    }

    public Instant getTermosAceitosEm() {
        return termosAceitosEm;
    }

    public boolean emailConfirmado() {
        return emailConfirmadoEm != null;
    }

    public void trocarSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    /** Marca o e-mail como confirmado. Não muda a data de quem já estava confirmado. */
    public void confirmarEmail(Instant agora) {
        if (emailConfirmadoEm == null) {
            emailConfirmadoEm = agora;
            atualizadoEm = agora;
        }
    }

    public void aceitarTermos(String versao, Instant agora) {
        this.termosVersao = versao;
        this.termosAceitosEm = agora;
    }

    public void renomear(String novoNome, Instant agora) {
        this.nome = novoNome;
        this.atualizadoEm = agora;
    }

    public void trocarEmail(String novoEmail, Instant agora) {
        this.email = novoEmail;
        this.atualizadoEm = agora;
    }
}
