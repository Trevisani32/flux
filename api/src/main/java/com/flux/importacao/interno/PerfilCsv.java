package com.flux.importacao.interno;

import java.time.Instant;

import com.flux.importacao.interno.leitura.FormatoCsv;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** O perfil de CSV de uma conta (decisão 12): um por conta, guardado como o {@link FormatoCsv}. */
@Entity
@Table(name = "perfis_csv")
public class PerfilCsv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormatoCsv.Separador separador;

    @Column(name = "linhas_de_cabecalho", nullable = false)
    private int linhasDeCabecalho;

    @Column(name = "coluna_data", nullable = false)
    private int colunaData;

    @Enumerated(EnumType.STRING)
    @Column(name = "formato_data", nullable = false, length = 10)
    private FormatoCsv.FormatoData formatoData;

    @Column(name = "coluna_descricao", nullable = false)
    private int colunaDescricao;

    @Column(name = "coluna_valor", nullable = false)
    private int colunaValor;

    @Column(name = "coluna_saidas")
    private Integer colunaSaidas;

    @Column(name = "coluna_identificador")
    private Integer colunaIdentificador;

    @Enumerated(EnumType.STRING)
    @Column(name = "separador_decimal", nullable = false, length = 10)
    private FormatoCsv.SeparadorDecimal separadorDecimal;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected PerfilCsv() {
    }

    public PerfilCsv(Long usuarioId, Long contaId) {
        this.usuarioId = usuarioId;
        this.contaId = contaId;
    }

    public void alterar(FormatoCsv formato, Instant agora) {
        this.separador = formato.separador();
        this.linhasDeCabecalho = formato.linhasDeCabecalho();
        this.colunaData = formato.colunaData();
        this.formatoData = formato.formatoData();
        this.colunaDescricao = formato.colunaDescricao();
        this.colunaValor = formato.colunaValor();
        this.colunaSaidas = formato.colunaSaidas();
        this.colunaIdentificador = formato.colunaIdentificador();
        this.separadorDecimal = formato.separadorDecimal();
        this.atualizadoEm = agora;
    }

    public FormatoCsv formato() {
        return new FormatoCsv(separador, linhasDeCabecalho, colunaData, formatoData, colunaDescricao, colunaValor, colunaSaidas,
                colunaIdentificador, separadorDecimal);
    }
}
