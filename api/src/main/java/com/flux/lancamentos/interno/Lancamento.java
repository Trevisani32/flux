package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Uma movimentação numa conta. O valor é guardado com sinal (ADR 0002): receita positiva,
 * despesa negativa; numa transferência, a saída é negativa e a entrada, positiva.
 */
@Entity
@Table(name = "lancamentos")
public class Lancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 255)
    private String descricao;

    @Column(name = "descricao_chave", nullable = false, length = 1020)
    private String descricaoChave;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLancamento tipo;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(length = 1000)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemLancamento origem;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Lancamento() {
    }

    /** Lançamento manual novo; {@code transferenciaId} só num lado de transferência. */
    public Lancamento(Long usuarioId, Long transferenciaId, Instant agora) {
        this.usuarioId = usuarioId;
        this.transferenciaId = transferenciaId;
        this.origem = OrigemLancamento.MANUAL;
        this.criadoEm = agora;
    }

    /** {@code valor} já com o sinal; a descrição já sem espaços sobrando. */
    public void alterar(TipoLancamento tipo, Long contaId, LocalDate data, String descricao, BigDecimal valor,
                        Long categoriaId, String observacao, Instant agora) {
        this.tipo = tipo;
        this.contaId = contaId;
        this.data = data;
        this.descricao = descricao;
        this.descricaoChave = TextoComparavel.chave(descricao);
        this.valor = valor.setScale(2);
        this.categoriaId = categoriaId;
        this.observacao = observacao;
        this.atualizadoEm = agora;
    }

    public boolean transferencia() {
        return tipo == TipoLancamento.TRANSFERENCIA;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public Long getContaId() {
        return contaId;
    }

    public LocalDate getData() {
        return data;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getDescricaoChave() {
        return descricaoChave;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public TipoLancamento getTipo() {
        return tipo;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public String getObservacao() {
        return observacao;
    }

    public OrigemLancamento getOrigem() {
        return origem;
    }

    public Long getTransferenciaId() {
        return transferenciaId;
    }
}
