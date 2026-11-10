package com.flux.categorias.interno;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Natureza natureza;

    @Column(name = "categoria_mae_id")
    private Long categoriaMaeId;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(name = "nome_chave", nullable = false, length = 240)
    private String nomeChave;

    @Column(name = "arquivada_em")
    private Instant arquivadaEm;

    @Column(name = "arquivada_com_a_mae", nullable = false)
    private boolean arquivadaComAMae;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Categoria() {
    }

    public Categoria(Long usuarioId, Natureza natureza, Long categoriaMaeId, String nome, String nomeChave, Instant agora) {
        this.usuarioId = usuarioId;
        this.natureza = natureza;
        this.categoriaMaeId = categoriaMaeId;
        this.nome = nome;
        this.nomeChave = nomeChave;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public void alterar(String nome, String nomeChave, Long categoriaMaeId, Instant agora) {
        this.nome = nome;
        this.nomeChave = nomeChave;
        this.categoriaMaeId = categoriaMaeId;
        this.atualizadaEm = agora;
    }

    /** {@code junto} = arquivada porque a categoria principal foi arquivada. */
    public void arquivar(Instant agora, boolean junto) {
        this.arquivadaEm = agora;
        this.arquivadaComAMae = junto;
        this.atualizadaEm = agora;
    }

    public void reativar(Instant agora) {
        this.arquivadaEm = null;
        this.arquivadaComAMae = false;
        this.atualizadaEm = agora;
    }

    public boolean arquivada() {
        return arquivadaEm != null;
    }

    public boolean principal() {
        return categoriaMaeId == null;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Natureza getNatureza() {
        return natureza;
    }

    public Long getCategoriaMaeId() {
        return categoriaMaeId;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeChave() {
        return nomeChave;
    }

    public Instant getArquivadaEm() {
        return arquivadaEm;
    }

    public boolean isArquivadaComAMae() {
        return arquivadaComAMae;
    }
}
