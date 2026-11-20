package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import com.flux.cartoes.CalendarioDoCartao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Uma fatura de cartão (ADR 0017), criada sob demanda com as datas do momento (decisão 9). O mês de
 * referência é o do vencimento, gravado como o dia 1 dele. Mudar o vencimento leva a data das parcelas
 * junto (ON UPDATE CASCADE, decisão 47).
 */
@Entity
@Table(name = "faturas")
public class Fatura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "cartao_id", nullable = false)
    private Long cartaoId;

    @Column(name = "mes_referencia", nullable = false)
    private LocalDate mesReferencia;

    @Column(name = "data_fechamento", nullable = false)
    private LocalDate dataFechamento;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "fechamento_ajustado", nullable = false)
    private boolean fechamentoAjustado;

    /** O fechamento pelos dias que valiam para esta fatura quando foi gravada ou recalculada (decisões 7 e 12). */
    @Column(name = "fechamento_normal", nullable = false)
    private LocalDate fechamentoNormal;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Fatura() {
    }

    public Fatura(Long usuarioId, Long cartaoId, YearMonth mes, CalendarioDoCartao.Datas datas, Instant agora) {
        this.usuarioId = usuarioId;
        this.cartaoId = cartaoId;
        this.mesReferencia = mes.atDay(1);
        this.dataFechamento = datas.fechamento();
        this.dataVencimento = datas.vencimento();
        this.fechamentoNormal = datas.fechamento();
        this.criadaEm = agora;
    }

    /** Decisão 12: o fechamento só desta fatura; {@code ajustado} falso é o fechamento normal, que fica guardado. */
    public void ajustarFechamento(LocalDate fechamento, boolean ajustado) {
        this.dataFechamento = fechamento;
        this.fechamentoAjustado = ajustado;
    }

    /** Decisão 7: as datas pelos dias novos do cartão, que passam a ser as normais dela; o ajuste se perde. */
    public void recalcular(CalendarioDoCartao.Datas datas) {
        this.dataFechamento = datas.fechamento();
        this.dataVencimento = datas.vencimento();
        this.fechamentoNormal = datas.fechamento();
        this.fechamentoAjustado = false;
    }

    public YearMonth mes() {
        return YearMonth.from(mesReferencia);
    }

    public CalendarioDoCartao.Datas datas() {
        return new CalendarioDoCartao.Datas(dataFechamento, dataVencimento);
    }

    public Long getId() {
        return id;
    }

    public Long getCartaoId() {
        return cartaoId;
    }

    public LocalDate getDataFechamento() {
        return dataFechamento;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public boolean isFechamentoAjustado() {
        return fechamentoAjustado;
    }

    public LocalDate getFechamentoNormal() {
        return fechamentoNormal;
    }
}
