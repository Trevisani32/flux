package com.flux.recorrencias.interno;

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
@Table(name = "ocorrencias_tratadas")
public class OcorrenciaTratada {

    enum Situacao {
        EFETIVADA,
        PULADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "recorrencia_id", nullable = false)
    private Long recorrenciaId;

    @Column(nullable = false)
    private LocalDate periodo;

    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Situacao situacao;

    @Column(name = "lancamento_id")
    private Long lancamentoId;

    @Column(name = "compra_id")
    private Long compraId;

    @Column(name = "tratada_em", nullable = false)
    private Instant tratadaEm;

    protected OcorrenciaTratada() {
    }

    public OcorrenciaTratada(Long usuarioId, Long recorrenciaId, LocalDate periodo) {
        this.usuarioId = usuarioId;
        this.recorrenciaId = recorrenciaId;
        this.periodo = periodo;
    }

    public void efetivar(LocalDate dataPrevista, Long lancamentoId, Long compraId, Instant agora) {
        this.dataPrevista = dataPrevista;
        this.situacao = Situacao.EFETIVADA;
        this.lancamentoId = lancamentoId;
        this.compraId = compraId;
        this.tratadaEm = agora;
    }

    public void pular(LocalDate dataPrevista, Instant agora) {
        this.dataPrevista = dataPrevista;
        this.situacao = Situacao.PULADA;
        this.lancamentoId = null;
        this.compraId = null;
        this.tratadaEm = agora;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public Long getRecorrenciaId() { return recorrenciaId; }
    public LocalDate getPeriodo() { return periodo; }
    public LocalDate getDataPrevista() { return dataPrevista; }
    public Situacao getSituacao() { return situacao; }
    public Long getLancamentoId() { return lancamentoId; }
    public Long getCompraId() { return compraId; }
}
