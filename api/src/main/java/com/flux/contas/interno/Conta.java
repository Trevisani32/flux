package com.flux.contas.interno;

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
@Table(name = "contas")
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(name = "nome_chave", nullable = false, length = 240)
    private String nomeChave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoConta tipo;

    @Column(name = "saldo_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoInicial;

    @Column(name = "data_saldo_inicial", nullable = false)
    private LocalDate dataSaldoInicial;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CorConta cor;

    @Column(name = "arquivada_em")
    private Instant arquivadaEm;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Conta() {
    }

    public Conta(Long usuarioId, String nome, String nomeChave, TipoConta tipo, BigDecimal saldoInicial,
                 LocalDate dataSaldoInicial, CorConta cor, Instant agora) {
        this.usuarioId = usuarioId;
        this.criadaEm = agora;
        alterar(nome, nomeChave, tipo, saldoInicial, dataSaldoInicial, cor, agora);
    }

    public void alterar(String nome, String nomeChave, TipoConta tipo, BigDecimal saldoInicial,
                        LocalDate dataSaldoInicial, CorConta cor, Instant agora) {
        this.nome = nome;
        this.nomeChave = nomeChave;
        this.tipo = tipo;
        this.saldoInicial = saldoInicial;
        this.dataSaldoInicial = dataSaldoInicial;
        this.cor = cor;
        this.atualizadaEm = agora;
    }

    public void arquivar(Instant agora) {
        this.arquivadaEm = agora;
        this.atualizadaEm = agora;
    }

    public void reativar(Instant agora) {
        this.arquivadaEm = null;
        this.atualizadaEm = agora;
    }

    public boolean arquivada() {
        return arquivadaEm != null;
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

    public TipoConta getTipo() {
        return tipo;
    }

    public BigDecimal getSaldoInicial() {
        return saldoInicial;
    }

    public LocalDate getDataSaldoInicial() {
        return dataSaldoInicial;
    }

    public CorConta getCor() {
        return cor;
    }

    public Instant getArquivadaEm() {
        return arquivadaEm;
    }
}
