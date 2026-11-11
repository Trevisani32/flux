package com.flux.importacao.interno;

import java.time.Instant;
import java.time.LocalDate;

import com.flux.importacao.interno.leitura.ExtratoLido;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * O registro de uma importação confirmada (ADR 0006): o arquivo não é guardado, só o nome, o
 * hash e as quantidades. Desfeita, ela fica no histórico e sai do aviso de arquivo repetido.
 */
@Entity
@Table(name = "importacoes")
public class Importacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "conta_id", nullable = false)
    private Long contaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private ExtratoLido.Formato formato;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "hash_arquivo", nullable = false, length = 64)
    private String hashArquivo;

    @Column(name = "extrato_hash", length = 64)
    private String extratoHash;

    @Column(name = "extrato_final", length = 4)
    private String extratoFinal;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(nullable = false)
    private int lidas;

    @Column(nullable = false)
    private int importadas;

    @Column(nullable = false)
    private int duplicadas;

    @Column(nullable = false)
    private int anteriores;

    @Column(nullable = false)
    private int fora;

    @Column(nullable = false)
    private int desmarcadas;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "desfeita_em")
    private Instant desfeitaEm;

    protected Importacao() {
    }

    public Importacao(Long usuarioId, Long contaId, ExtratoLido.Formato formato, String nomeArquivo, String hashArquivo,
                      String extratoHash, String extratoFinal, LocalDate dataInicio, LocalDate dataFim, Quantidades quantidades,
                      Instant agora) {
        this.usuarioId = usuarioId;
        this.contaId = contaId;
        this.formato = formato;
        this.nomeArquivo = nomeArquivo;
        this.hashArquivo = hashArquivo;
        this.extratoHash = extratoHash;
        this.extratoFinal = extratoFinal;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.lidas = quantidades.lidas();
        this.importadas = quantidades.importadas();
        this.duplicadas = quantidades.duplicadas();
        this.anteriores = quantidades.anteriores();
        this.fora = quantidades.fora();
        this.desmarcadas = quantidades.desmarcadas();
        this.criadaEm = agora;
    }

    /** As quantidades do registro: {@code lidas} é a soma das outras cinco (CHECK da V5). */
    public record Quantidades(int lidas, int importadas, int duplicadas, int anteriores, int fora, int desmarcadas) {
    }

    public void desfazer(Instant agora) {
        this.desfeitaEm = agora;
    }

    public boolean desfeita() {
        return desfeitaEm != null;
    }

    public Long getId() {
        return id;
    }

    public Long getContaId() {
        return contaId;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public String getExtratoFinal() {
        return extratoFinal;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public int getImportadas() {
        return importadas;
    }

    public int getDuplicadas() {
        return duplicadas;
    }

    public int getAnteriores() {
        return anteriores;
    }

    public int getFora() {
        return fora;
    }

    public int getDesmarcadas() {
        return desmarcadas;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getDesfeitaEm() {
        return desfeitaEm;
    }
}
