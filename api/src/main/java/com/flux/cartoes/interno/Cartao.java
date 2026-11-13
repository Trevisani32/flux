package com.flux.cartoes.interno;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Um cartão de crédito (decisões 1 a 6). Nenhum número, bandeira ou final de cartão é guardado. */
@Entity
@Table(name = "cartoes")
public class Cartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(name = "nome_chave", nullable = false, length = 240)
    private String nomeChave;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal limite;

    @Column(name = "dia_fechamento", nullable = false)
    private int diaFechamento;

    @Column(name = "dia_vencimento", nullable = false)
    private int diaVencimento;

    @Column(name = "conta_pagamento_id", nullable = false)
    private Long contaPagamentoId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CorCartao cor;

    @Column(name = "arquivado_em")
    private Instant arquivadoEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Cartao() {
    }

    public Cartao(Long usuarioId, Instant agora) {
        this.usuarioId = usuarioId;
        this.criadoEm = agora;
    }

    public void alterar(String nome, String nomeChave, BigDecimal limite, int diaFechamento, int diaVencimento,
                        Long contaPagamentoId, CorCartao cor, Instant agora) {
        this.nome = nome;
        this.nomeChave = nomeChave;
        this.limite = limite.setScale(2);
        this.diaFechamento = diaFechamento;
        this.diaVencimento = diaVencimento;
        this.contaPagamentoId = contaPagamentoId;
        this.cor = cor;
        this.atualizadoEm = agora;
    }

    public void arquivar(Instant agora) {
        this.arquivadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void reativar(Instant agora) {
        this.arquivadoEm = null;
        this.atualizadoEm = agora;
    }

    public boolean arquivado() {
        return arquivadoEm != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeChave() {
        return nomeChave;
    }

    public BigDecimal getLimite() {
        return limite;
    }

    public int getDiaFechamento() {
        return diaFechamento;
    }

    public int getDiaVencimento() {
        return diaVencimento;
    }

    public Long getContaPagamentoId() {
        return contaPagamentoId;
    }

    public CorCartao getCor() {
        return cor;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }
}
