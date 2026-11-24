package com.flux.importacao.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.flux.importacao.interno.Classificador.Motivo;
import com.flux.importacao.interno.Classificador.Situacao;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.lancamentos.LancamentosImportados.ExistenteNoCartao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static java.time.Duration.ofSeconds;

/** Decisões 40 a 43 e ADR 0019: reconhecimento, chaves e classificação pura da fatura. */
class ClassificadorDaFaturaTest {

    private static final long CARTAO = 11L;
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);
    private static final YearMonth FATURA = YearMonth.of(2026, 10);
    private static final Instant CRIADO = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void reconheceAParcelaNoFimDaDescricao() {
        for (String descricao : List.of("Loja Parcela 3/10", "Loja - parcela 3 / 10", "Loja — PARCELA 3/10")) {
            assertThat(ParcelaNaDescricao.de(descricao)).hasValueSatisfying(parcela -> {
                assertThat(parcela.descricao()).isEqualTo("Loja");
                assertThat(parcela.numero()).isEqualTo(3);
                assertThat(parcela.parcelas()).isEqualTo(10);
            });
        }
        for (String descricao : List.of("Loja Parcela 11/10", "Loja Parcela 1/1", "Loja Parcela 2/49", "Parcela 2/3")) {
            assertThat(ParcelaNaDescricao.de(descricao)).isEmpty();
        }
        assertTimeoutPreemptively(ofSeconds(1), () -> {
            String longa = "Loja " + "- ".repeat(200_000) + "fim";
            assertThat(ParcelaNaDescricao.de(longa)).isEmpty();
            assertThat(ParcelaNaDescricao.de(longa + " Parcela 3/10")).isEmpty();
        });
    }

    @Test
    void aChaveDaParcelaEAMesmaNasFaturasSeguintesComOutroNumero() {
        ExtratoLido.Linha terceira = linha(1, "2026-09-03", "-99.90", "Curso - Parcela 3/10", null);
        ExtratoLido.Linha quarta = linha(1, "2026-09-03", "-99.90", "Curso - Parcela 4/10", null);
        String baseDaTerceira = Chaves.daFatura(CARTAO, List.of(terceira), ClassificadorDaFatura.parcelas(List.of(terceira))).get(1).base();
        String baseDaQuarta = Chaves.daFatura(CARTAO, List.of(quarta), ClassificadorDaFatura.parcelas(List.of(quarta))).get(1).base();
        assertThat(baseDaTerceira).isEqualTo(baseDaQuarta);
        assertThat(baseDaTerceira + ":3").isNotEqualTo(baseDaQuarta + ":4");
    }

    @Test
    void oPagamentoOTotalEADataForaDaFaixaFicamDeFora() {
        assertThat(ClassificadorDaFatura.motivoDeFora(linha(1, "2026-10-02", "20.00", "Pagamento recebido", null), HOJE.plusMonths(12),
                HOJE.minusYears(10))).isEqualTo(Motivo.PAGAMENTO_DA_FATURA);
        assertThat(ClassificadorDaFatura.motivoDeFora(linha(2, "2026-10-02", "-20.00", "Total da fatura", null), HOJE.plusMonths(12),
                HOJE.minusYears(10))).isEqualTo(Motivo.LINHA_DE_SALDO);
        assertThat(ClassificadorDaFatura.motivoDeFora(linha(3, "2016-11-09", "-20.00", "Loja", null), HOJE.plusMonths(12),
                HOJE.minusYears(10))).isEqualTo(Motivo.DATA_ANTES_DO_LIMITE);
        assertThat(ClassificadorDaFatura.motivoDeFora(linha(4, "2027-11-11", "-20.00", "Loja", null), HOJE.plusMonths(12),
                HOJE.minusYears(10))).isEqualTo(Motivo.DATA_ALEM_DO_LIMITE);
    }

    @Test
    void aProvavelDuplicadaEAParcelaNaMesmaFaturaOuACompraAVistaDoMesmoDia() {
        List<ExistenteNoCartao> existentes = List.of(existente(1, "2026-10-05", FATURA, "-22.50", "Outra descrição", null, 1, 1, null),
                existente(2, "2026-09-03", FATURA, "-99.90", "Curso", null, 3, 10, null));
        List<ClassificadorDaFatura.Classificada> resultado = ClassificadorDaFatura.classificar(CARTAO,
                List.of(linha(1, "2026-10-05", "-22.50", "Padaria", null), linha(2, "2026-09-03", "-99.90", "Curso Parcela 3/10", null)),
                existentes, FATURA, HOJE);
        assertThat(resultado).extracting(ClassificadorDaFatura.Classificada::situacao).containsExactly(Situacao.PROVAVEL, Situacao.PROVAVEL);
        assertThat(resultado).allMatch(classificada -> !classificada.marcada());
    }

    @Test
    void aChaveQueJaExisteEAJaImportadaEAParcelaAnteriorDaCompraFicaDeFora() {
        ExtratoLido.Linha primeira = linha(1, "2026-09-03", "-99.90", "Curso Parcela 3/10", null);
        String chave = Chaves.daFatura(CARTAO, List.of(primeira), ClassificadorDaFatura.parcelas(List.of(primeira))).get(1).base() + ":3";
        List<ClassificadorDaFatura.Classificada> resultado = ClassificadorDaFatura.classificar(CARTAO, List.of(primeira),
                List.of(existente(4, "2026-09-03", FATURA, "-99.90", "Curso", "Curso", 3, 10, chave)), FATURA, HOJE);
        assertThat(resultado.getFirst().situacao()).isEqualTo(Situacao.JA_IMPORTADA);

        ExtratoLido.Linha quarta = linha(2, "2026-09-03", "-99.90", "Curso Parcela 4/10", null);
        String base = Chaves.daFatura(CARTAO, List.of(quarta), ClassificadorDaFatura.parcelas(List.of(quarta))).get(2).base();
        List<ClassificadorDaFatura.Classificada> anterior = ClassificadorDaFatura.classificar(CARTAO, List.of(quarta),
                List.of(existente(5, "2026-09-03", FATURA, "-99.90", "Curso", "Curso", 5, 10, base + ":5")), FATURA, HOJE);
        assertThat(anterior.getFirst()).extracting(ClassificadorDaFatura.Classificada::situacao, ClassificadorDaFatura.Classificada::motivo)
                .containsExactly(Situacao.FORA, Motivo.COMPRA_JA_NO_FLUX);
    }

    @Test
    void aParcelaJaImportadaComOutrosCentavosFicaComOValorDoBanco() {
        ExtratoLido.Linha linha = linha(1, "2026-09-03", "-33.33", "Curso Parcela 2/3", null);
        String chave = Chaves.daFatura(CARTAO, List.of(linha), ClassificadorDaFatura.parcelas(List.of(linha))).get(1).base() + ":2";
        ClassificadorDaFatura.Classificada classificada = ClassificadorDaFatura.classificar(CARTAO, List.of(linha),
                List.of(existente(1, "2026-09-03", FATURA, "-33.34", "Curso", "Curso", 2, 3, chave)), FATURA, HOJE).getFirst();
        assertThat(classificada.situacao()).isEqualTo(Situacao.JA_IMPORTADA);
        assertThat(classificada.valorNoFlux()).isEqualByComparingTo("-33.34");
    }

    @Test
    void oExportParcialEDepoisOCompletoReencontraACompra() {
        ExtratoLido.Linha parcial = linha(1, "2026-09-03", "-33.34", "Curso Parcela 1/3", null);
        String base = Chaves.daFatura(CARTAO, List.of(parcial), ClassificadorDaFatura.parcelas(List.of(parcial))).get(1).base();
        List<ExtratoLido.Linha> completo = List.of(parcial, linha(2, "2026-09-03", "-33.37", "Curso Parcela 1/3", null));
        List<ClassificadorDaFatura.Classificada> resultado = ClassificadorDaFatura.classificar(CARTAO, completo,
                List.of(existente(1, "2026-09-03", FATURA, "-33.34", "Curso", "Curso", 1, 3, base + ":1")), FATURA, HOJE);
        assertThat(resultado).extracting(ClassificadorDaFatura.Classificada::situacao).containsExactly(Situacao.JA_IMPORTADA, Situacao.NOVA);
    }

    @Test
    void osCentavosDaDivisaoNaoSeparamAMesmaCompra() {
        assertThat(ClassificadorDaFatura.mesmoValor(new BigDecimal("-33.34"), new BigDecimal("-33.33"), 3)).isTrue();
        assertThat(ClassificadorDaFatura.mesmoValor(new BigDecimal("-33.34"), new BigDecimal("-33.37"), 3)).isFalse();
    }

    private static ExtratoLido.Linha linha(int numero, String data, String valor, String descricao, String identificador) {
        return new ExtratoLido.Linha(numero, LocalDate.parse(data), new BigDecimal(valor), descricao, descricao, identificador, null);
    }

    private static ExistenteNoCartao existente(long id, String data, YearMonth mes, String valor, String descricao, String descricaoBanco,
                                                int parcela, int parcelas, String chave) {
        return new ExistenteNoCartao(id, LocalDate.parse(data), mes, new BigDecimal(valor), descricao, descricaoBanco, parcela, parcelas,
                chave, chave == null ? null : 1L, CRIADO, false);
    }
}
