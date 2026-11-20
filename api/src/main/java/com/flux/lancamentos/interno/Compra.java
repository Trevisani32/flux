package com.flux.lancamentos.interno;

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

/**
 * Uma compra no cartão ou um estorno (ADR 0017): junta as parcelas, que são lançamentos na fatura. A
 * descrição, a categoria e a observação ficam em cada parcela; aqui fica o que define as parcelas.
 */
@Entity
@Table(name = "compras")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "cartao_id", nullable = false)
    private Long cartaoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoCompra tipo;

    @Column(name = "data_compra", nullable = false)
    private LocalDate dataCompra;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private int parcelas;

    @Column(name = "parcela_inicial", nullable = false)
    private int parcelaInicial;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private Instant atualizadaEm;

    protected Compra() {
    }

    public Compra(Long usuarioId, TipoCompra tipo, Instant agora) {
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.criadaEm = agora;
    }

    /** {@code valorTotal} positivo; o estorno tem uma parcela só. */
    public void alterar(Long cartaoId, LocalDate dataCompra, BigDecimal valorTotal, int parcelas, int parcelaInicial, Instant agora) {
        this.cartaoId = cartaoId;
        this.dataCompra = dataCompra;
        this.valorTotal = valorTotal.setScale(2);
        this.parcelas = parcelas;
        this.parcelaInicial = parcelaInicial;
        this.atualizadaEm = agora;
    }

    public boolean estorno() {
        return tipo == TipoCompra.ESTORNO;
    }

    public Long getId() {
        return id;
    }

    public Long getCartaoId() {
        return cartaoId;
    }

    public TipoCompra getTipo() {
        return tipo;
    }

    public LocalDate getDataCompra() {
        return dataCompra;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public int getParcelas() {
        return parcelas;
    }

    public int getParcelaInicial() {
        return parcelaInicial;
    }
}
