package com.flux.projecao.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjecaoResponse(LocalDate hoje, LocalDate ate, int diasDepoisDeHoje, Periodos periodos, List<Conta> contas,
                               List<Long> escolhidas, Resumo resumo, List<ResumoDaConta> porConta, List<Item> pendencias,
                               List<Dia> dias, List<FaturaFora> faturasFora) {

    public record Periodos(LocalDate padrao, LocalDate tresMeses, LocalDate seisMeses, LocalDate dozeMeses) {
    }

    public record Conta(Long id, String nome, String tipo, String cor, boolean padrao, boolean escolhida) {
    }

    public record Trecho(LocalDate de, LocalDate ate) {
    }

    public record Resumo(BigDecimal hoje, BigDecimal pendencias, BigDecimal inicio, BigDecimal menor, LocalDate diaDoMenor,
                         BigDecimal fim, int diasNegativos, List<Trecho> trechosNegativos) {
    }

    public record ResumoDaConta(Long contaId, String nome, String cor, BigDecimal hoje, BigDecimal pendencias,
                                BigDecimal inicio, BigDecimal menor, LocalDate diaDoMenor, BigDecimal fim, int diasNegativos,
                                List<Trecho> trechosNegativos) {
    }

    public record Item(String tipo, LocalDate data, String descricao, BigDecimal valor, boolean valorVaria, Long contaId,
                       String contaNome, Long lancamentoId, Long outraContaId, String outraContaNome, Long cartaoId,
                       String cartaoNome, String cartaoCor, String mes, LocalDate vencimento, BigDecimal pendente,
                       BigDecimal previsto, List<PrevistoDaFatura> previstos, OcorrenciaDaProjecao ocorrencia) {
    }

    public record PrevistoDaFatura(Long recorrenciaId, LocalDate periodo, LocalDate data, String descricao, BigDecimal valor,
                                   OcorrenciaDaProjecao ocorrencia) {
    }

    public record OcorrenciaDaProjecao(Long recorrenciaId, LocalDate periodo, LocalDate data, LocalDate dataNaLista,
                                       String descricao, String tipo, BigDecimal valor, BigDecimal valorPrevisto,
                                       boolean valorVaria, Long contaId, String contaNome, Long cartaoId, String cartaoNome,
                                       String cartaoCor, Long categoriaId, String categoriaNome, String categoriaMaeNome,
                                       boolean atrasada, LocalDate visivelAte) {
    }

    public record Dia(LocalDate data, BigDecimal movimento, BigDecimal saldo, List<Item> itens) {
    }

    public record FaturaFora(Long cartaoId, String cartaoNome, Long contaId, String contaNome, List<String> meses,
                             BigDecimal valor) {
    }
}
