package com.flux.categorizacao.interno;

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
 * Uma regra de categorização (ADR 0015). {@code natureza} é a da categoria ("RECEITA" ou "DESPESA"):
 * a FK com a natureza garante que as duas andam juntas. {@code contaId} nulo = todas as contas.
 */
@Entity
@Table(name = "regras_categorizacao")
public class Regra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private int posicao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Condicao condicao;

    @Column(nullable = false, length = 255)
    private String texto;

    @Column(name = "texto_chave", nullable = false, length = 1020)
    private String textoChave;

    @Column(name = "conta_id")
    private Long contaId;

    @Column(nullable = false, length = 10)
    private String natureza;

    @Column(name = "categoria_id", nullable = false)
    private Long categoriaId;

    @Column(length = 255)
    private String descricao;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Regra() {
    }

    public Regra(Long usuarioId, int posicao, Instant agora) {
        this.usuarioId = usuarioId;
        this.posicao = posicao;
        this.criadaEm = agora;
    }

    /** {@code texto} e {@code descricao} já sem espaços sobrando; a chave é calculada aqui. */
    public void alterar(Condicao condicao, String texto, Long contaId, String natureza, Long categoriaId, String descricao,
                        Instant agora) {
        this.condicao = condicao;
        this.texto = texto;
        this.textoChave = Comparacao.chave(texto);
        this.contaId = contaId;
        this.natureza = natureza;
        this.categoriaId = categoriaId;
        this.descricao = descricao;
        this.atualizadaEm = agora;
    }

    public void mudarPosicao(int posicao) {
        this.posicao = posicao;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public int getPosicao() {
        return posicao;
    }

    public Condicao getCondicao() {
        return condicao;
    }

    public String getTexto() {
        return texto;
    }

    public String getTextoChave() {
        return textoChave;
    }

    public Long getContaId() {
        return contaId;
    }

    public String getNatureza() {
        return natureza;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public String getDescricao() {
        return descricao;
    }
}
