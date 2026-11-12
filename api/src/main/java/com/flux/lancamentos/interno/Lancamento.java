package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

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

    /** Só no importado (ADR 0006): a importação, a chave de idempotência e a descrição do banco. */
    @Column(name = "importacao_id")
    private Long importacaoId;

    @Column(name = "chave_importacao", length = 300)
    private String chaveImportacao;

    @Column(name = "descricao_banco", length = 255)
    private String descricaoBanco;

    /** Importado excluído (decisão 30): fica fora da lista e do saldo, e a chave continua. */
    @Column(name = "ignorado_em")
    private Instant ignoradoEm;

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

    /**
     * Linha de extrato confirmada na importação: receita se o valor é positivo, despesa se é
     * negativo (ADR 0002). {@code descricaoBanco} é a do extrato, já sem espaços sobrando;
     * {@code descricao} é a que a revisão mostrou (a legível da regra, Fatia 04b) ou a mesma do extrato.
     */
    public static Lancamento importado(Long usuarioId, Long contaId, Long importacaoId, LocalDate data, BigDecimal valor,
                                       String descricao, String descricaoBanco, String chave, Long categoriaId, Instant agora) {
        Lancamento lancamento = new Lancamento(usuarioId, null, agora);
        lancamento.origem = OrigemLancamento.IMPORTADO;
        lancamento.importacaoId = importacaoId;
        lancamento.chaveImportacao = chave;
        lancamento.descricaoBanco = descricaoBanco;
        lancamento.alterar(valor.signum() > 0 ? TipoLancamento.RECEITA : TipoLancamento.DESPESA, contaId, data, descricao, valor,
                categoriaId, null, agora);
        return lancamento;
    }

    /**
     * Uma regra categorizou (Fatia 04b, decisão 18): põe a categoria e, no importado cuja descrição
     * ainda é a do extrato, a descrição legível da regra, se houver. Muda {@code atualizado_em}.
     */
    public void categorizarPorRegra(Long categoriaId, String descricaoLegivel, Instant agora) {
        String novaDescricao = descricaoLegivel != null && importado() && descricao.equals(descricaoBanco) ? descricaoLegivel : descricao;
        alterar(tipo, contaId, data, novaDescricao, valor, categoriaId, observacao, agora);
    }

    /**
     * No importado só mudam descrição, categoria e observação (decisão 29). Sem nenhuma mudança,
     * {@code atualizado_em} fica como está: abrir e salvar não conta como "editado" no desfazer.
     */
    public void alterarImportado(String descricao, Long categoriaId, String observacao, Instant agora) {
        if (Objects.equals(this.descricao, descricao) && Objects.equals(this.categoriaId, categoriaId)
                && Objects.equals(this.observacao, observacao)) {
            return;
        }
        alterar(tipo, contaId, data, descricao, valor, categoriaId, observacao, agora);
    }

    /** Excluir um importado (decisão 30): ignora e tira a categoria; não mexe em atualizado_em. */
    public void ignorar(Instant agora) {
        this.ignoradoEm = agora;
        this.categoriaId = null;
    }

    /** Volta para a lista, com a categoria dada (ou sem nenhuma). */
    public void restaurar(Long categoriaId) {
        this.ignoradoEm = null;
        this.categoriaId = categoriaId;
    }

    /**
     * Fatia 04c (decisão 29): a receita ou despesa vira um lado da transferência. Data, valor, conta,
     * observação, descrição do extrato e chave de importação ficam; a categoria sai e a descrição passa a
     * ser a da transferência. O lado novo, lançado na conversão, não passa por aqui.
     */
    public void converterEmLado(Long transferenciaId, String descricaoDaTransferencia, Instant agora) {
        this.transferenciaId = transferenciaId;
        alterar(TipoLancamento.TRANSFERENCIA, contaId, data, descricaoDaTransferencia, valor, null, observacao, agora);
    }

    /**
     * Fatia 04c (decisão 32): o lado sai da transferência e volta a ser receita ou despesa, pelo sinal,
     * sem categoria. O importado volta com a descrição do extrato; o manual fica com a descrição que tem.
     * Quem chama apaga o vínculo depois de gravar isto.
     */
    public void soltarDaTransferencia(Instant agora) {
        this.transferenciaId = null;
        alterar(valor.signum() > 0 ? TipoLancamento.RECEITA : TipoLancamento.DESPESA, contaId, data,
                importado() ? descricaoBanco : descricao, valor, null, observacao, agora);
    }

    /**
     * Fatia 04c (decisão 31): na transferência convertida só mudam a descrição e a observação. Sem
     * mudança, {@code atualizado_em} fica como está, como no importado.
     */
    public void alterarLadoConvertido(String descricao, String observacao, Instant agora) {
        if (Objects.equals(this.descricao, descricao) && Objects.equals(this.observacao, observacao)) {
            return;
        }
        alterar(tipo, contaId, data, descricao, valor, null, observacao, agora);
    }

    public boolean transferencia() {
        return tipo == TipoLancamento.TRANSFERENCIA;
    }

    public boolean importado() {
        return origem == OrigemLancamento.IMPORTADO;
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

    public Long getImportacaoId() {
        return importacaoId;
    }

    public String getChaveImportacao() {
        return chaveImportacao;
    }

    public String getDescricaoBanco() {
        return descricaoBanco;
    }

    public Instant getIgnoradoEm() {
        return ignoradoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}