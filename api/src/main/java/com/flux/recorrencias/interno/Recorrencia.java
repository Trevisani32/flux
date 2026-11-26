package com.flux.recorrencias.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recorrencias")
public class Recorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDaRecorrencia tipo;

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(name = "conta_id")
    private Long contaId;

    @Column(name = "cartao_id")
    private Long cartaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequencia frequencia;

    @Column(name = "dia_do_mes")
    private Integer diaDoMes;

    @Column(name = "comeca_em", nullable = false)
    private LocalDate comecaEm;

    @Column(name = "termina_em")
    private LocalDate terminaEm;

    @Column(name = "valor_varia", nullable = false)
    private boolean valorVaria;

    @Column(name = "ocorrencias_desde", nullable = false)
    private LocalDate ocorrenciasDesde;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Recorrencia() {
    }

    public Recorrencia(Long usuarioId, LocalDate ocorrenciasDesde, Instant agora) {
        this.usuarioId = usuarioId;
        this.ocorrenciasDesde = ocorrenciasDesde;
        this.criadaEm = agora;
        this.atualizadaEm = agora;
    }

    public void alterar(String descricao, BigDecimal valor, TipoDaRecorrencia tipo, Long categoriaId, Long contaId,
                        Long cartaoId, Frequencia frequencia, Integer diaDoMes, LocalDate comecaEm, LocalDate terminaEm,
                        boolean valorVaria, Instant agora) {
        this.descricao = descricao;
        this.valor = valor;
        this.tipo = tipo;
        this.categoriaId = categoriaId;
        this.contaId = contaId;
        this.cartaoId = cartaoId;
        this.frequencia = frequencia;
        this.diaDoMes = frequencia == Frequencia.MENSAL ? diaDoMes : null;
        this.comecaEm = comecaEm;
        this.terminaEm = terminaEm;
        this.valorVaria = valorVaria;
        this.atualizadaEm = agora;
    }

    public CalendarioDaRecorrencia calendario() {
        return new CalendarioDaRecorrencia(frequencia, diaDoMes, comecaEm, terminaEm, ocorrenciasDesde);
    }

    public BigDecimal valorComSinal() {
        return tipo == TipoDaRecorrencia.DESPESA ? valor.negate() : valor;
    }

    public void encerrar(LocalDate hoje, Instant agora) {
        if (terminaEm == null || terminaEm.isAfter(hoje)) {
            terminaEm = hoje;
            atualizadaEm = agora;
        }
    }

    public void retomar(LocalDate hoje, Instant agora) {
        if (encerrada(hoje)) {
            terminaEm = null;
            ocorrenciasDesde = hoje;
            atualizadaEm = agora;
        }
    }

    public boolean encerrada(LocalDate hoje) {
        return terminaEm != null && !terminaEm.isAfter(hoje);
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getDescricao() { return descricao; }
    public BigDecimal getValor() { return valor; }
    public TipoDaRecorrencia getTipo() { return tipo; }
    public Long getCategoriaId() { return categoriaId; }
    public Long getContaId() { return contaId; }
    public Long getCartaoId() { return cartaoId; }
    public Frequencia getFrequencia() { return frequencia; }
    public Integer getDiaDoMes() { return diaDoMes; }
    public LocalDate getComecaEm() { return comecaEm; }
    public LocalDate getTerminaEm() { return terminaEm; }
    public boolean isValorVaria() { return valorVaria; }
    public LocalDate getOcorrenciasDesde() { return ocorrenciasDesde; }
    public Instant getCriadaEm() { return criadaEm; }
}
