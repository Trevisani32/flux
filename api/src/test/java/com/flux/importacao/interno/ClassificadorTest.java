package com.flux.importacao.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.flux.importacao.interno.Classificador.Classificada;
import com.flux.importacao.interno.Classificador.Motivo;
import com.flux.importacao.interno.Classificador.Situacao;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.importacao.interno.leitura.LeitorOfx;
import com.flux.lancamentos.LancamentosImportados.Existente;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** Decisões 14 a 19 e o ADR 0006: chave, ordinal, salvaguarda e a ordem da classificação. */
class ClassificadorTest {

    private static final long CONTA = 11;
    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);
    private static final LocalDate SALDO_INICIAL = LocalDate.of(2026, 10, 1);
    private static final Instant CRIADO = Instant.parse("2026-11-04T23:41:00Z");
    private static final Instant EXCLUIDO = Instant.parse("2026-11-06T12:00:00Z");

    @Test
    void osDoisCafesGanhamChavesDiferentesEUmPeriodoSobrepostoReconheceOsDois() {
        List<ExtratoLido.Linha> primeiro = linhas(
                linha(1, "2026-11-10", "-8.00", "PADARIA", null),
                linha(2, "2026-11-12", "-6.00", "CAFE DA ESQUINA", null),
                linha(3, "2026-11-12", "-6.00", "CAFE DA ESQUINA", null));
        Map<Integer, Chaves.DaLinha> chaves = Chaves.doArquivo(CONTA, primeiro);
        assertThat(chaves.get(2).doConteudo()).startsWith("CON:11:").isNotEqualTo(chaves.get(3).doConteudo());
        List<Existente> gravados = List.of(
                importado(1, "2026-11-12", "-6.00", "CAFE DA ESQUINA", chaves.get(2).doConteudo()),
                importado(2, "2026-11-12", "-6.00", "CAFE DA ESQUINA", chaves.get(3).doConteudo()));

        // O segundo arquivo escreve o café com acento e espaços sobrando: a normalização é a dos nomes.
        List<ExtratoLido.Linha> segundo = linhas(
                linha(1, "2026-11-12", "-6.00", "CAFE DA ESQUINA", null),
                linha(2, "2026-11-12", "-6.00", "CAFÉ  DA ESQUINA ", null),
                linha(3, "2026-11-20", "-30.00", "FARMACIA", null));

        assertThat(situacoes(Classificador.classificar(CONTA, segundo, gravados, SALDO_INICIAL, HOJE)))
                .containsExactly(Situacao.JA_IMPORTADA, Situacao.JA_IMPORTADA, Situacao.NOVA);
    }

    @Test
    void identificadorUnicoViraChaveIdEORepetidoNoArquivoCaiNaDoConteudo() {
        Map<Integer, Chaves.DaLinha> chaves = Chaves.doArquivo(CONTA, linhas(
                linha(1, "2026-11-05", "-6.00", "CAFE", "20261105001"),
                linha(2, "2026-11-05", "-6.00", "CAFE", "20261105001"),
                linha(3, "2026-11-06", "-9.00", "PADARIA", "f-3")));

        assertThat(chaves.get(1).doIdentificador()).isNull();
        assertThat(chaves.get(2).doIdentificador()).isNull();
        assertThat(chaves.get(3).doIdentificador()).isEqualTo("ID:11:f-3");
    }

    /** O FITID de data + sequência do dia muda quando o banco encaixa uma tarifa antes do café. */
    @Test
    void salvaguardaDoIdentificadorNaoPerdeATarifaNemDuplicaOMercado() {
        List<Existente> gravados = List.of(
                importado(1, "2026-11-05", "-6.00", "CAFE DA ESQUINA", "ID:11:20261105001"),
                importado(2, "2026-11-05", "-120.00", "MERCADO", "ID:11:20261105002"));
        List<ExtratoLido.Linha> segundaExportacao = linhas(
                linha(1, "2026-11-05", "-9.90", "TARIFA", "20261105001"),
                linha(2, "2026-11-05", "-6.00", "CAFE DA ESQUINA", "20261105002"),
                linha(3, "2026-11-05", "-120.00", "MERCADO", "20261105003"));

        List<Classificada> classificadas = Classificador.classificar(CONTA, segundaExportacao, gravados, SALDO_INICIAL, HOJE);

        assertThat(situacoes(classificadas)).containsExactly(Situacao.NOVA, Situacao.PROVAVEL, Situacao.PROVAVEL);
        assertThat(classificadas.get(0).chave()).startsWith("CON:11:");
        assertThat(classificadas.get(1).parecido().id()).isEqualTo(1);
        assertThat(classificadas.get(2).parecido().id()).isEqualTo(2);
    }

    @Test
    void ficamDeForaAntesDeOlharOBanco() {
        List<Classificada> classificadas = Classificador.classificar(CONTA, linhas(
                new ExtratoLido.Linha(1, null, null, null, null, null, "data ilegível"),
                linha(2, "2026-11-03", "0.00", "IOF ADICIONAL", null),
                linha(3, "2026-09-28", "3012.40", "SALDO ANTERIOR", null),
                linha(4, "2026-11-04", "-1.00", "Saldo do dia", null),
                linha(5, "2027-11-11", "-1.00", "MUITO LONGE", null),
                linha(6, "2027-11-10", "-1.00", "NO LIMITE", null)), List.of(), SALDO_INICIAL, HOJE);

        assertThat(classificadas).extracting(Classificada::situacao, Classificada::motivo).containsExactly(
                tuple(Situacao.FORA, Motivo.ILEGIVEL),
                tuple(Situacao.FORA, Motivo.VALOR_ZERO),
                tuple(Situacao.FORA, Motivo.LINHA_DE_SALDO),
                tuple(Situacao.FORA, Motivo.LINHA_DE_SALDO),
                tuple(Situacao.FORA, Motivo.DATA_ALEM_DO_LIMITE),
                tuple(Situacao.NOVA, null));
        assertThat(classificadas.get(5).futuro()).isTrue();
        assertThat(classificadas.get(5).marcada()).isFalse();
    }

    @Test
    void jaImportadaIncluiAExcluidaEVemAntesDoSaldoInicial() {
        List<ExtratoLido.Linha> arquivo = linhas(
                linha(1, "2026-09-29", "-29.90", "TARIFA", "t-1"),
                linha(2, "2026-09-30", "80.00", "PIX RECEBIDO", "t-2"),
                linha(3, "2026-11-01", "-300.00", "SAQUE 24H", "t-3"));
        List<Existente> gravados = List.of(
                new Existente(7L, LocalDate.parse("2026-09-29"), new BigDecimal("-29.90"), "TARIFA", "TARIFA", "ID:11:t-1", 4L, CRIADO,
                        null),
                new Existente(8L, LocalDate.parse("2026-11-01"), new BigDecimal("-300.00"), "SAQUE 24H", "SAQUE 24H", "ID:11:t-3", 4L,
                        CRIADO, EXCLUIDO));

        List<Classificada> classificadas = Classificador.classificar(CONTA, arquivo, gravados, SALDO_INICIAL, HOJE);

        assertThat(situacoes(classificadas))
                .containsExactly(Situacao.JA_IMPORTADA, Situacao.ANTES_DO_SALDO_INICIAL, Situacao.JA_IMPORTADA);
        assertThat(classificadas.get(2).excluida()).isTrue();
        assertThat(classificadas).noneMatch(Classificada::marcada);
    }

    @Test
    void provavelPegaOManualDeQualquerDescricaoEOImportadoSoComAMesmaUmPorUm() {
        List<Existente> existentes = List.of(
                new Existente(1L, LocalDate.parse("2026-11-06"), new BigDecimal("-23.90"), "Uber", null, null, null, CRIADO, null),
                new Existente(2L, LocalDate.parse("2026-11-07"), new BigDecimal("-64.00"), "Cinema do shopping", "CINEMA ARCOPLEX",
                        "CON:11:outra", 4L, CRIADO, null),
                new Existente(3L, LocalDate.parse("2026-11-08"), new BigDecimal("-42.80"), "Farmácia", "FARMACIA CENTRO", "CON:11:x", 4L,
                        CRIADO, null));
        List<ExtratoLido.Linha> arquivo = linhas(
                linha(1, "2026-11-06", "-23.90", "UBER *TRIP", null),
                linha(2, "2026-11-06", "-23.90", "UBER *TRIP", null),
                linha(3, "2026-11-07", "-64.00", "cinema arcoplex", null),
                linha(4, "2026-11-08", "-42.80", "FARMACIA BEM ESTAR", null));

        List<Classificada> classificadas = Classificador.classificar(CONTA, arquivo, existentes, SALDO_INICIAL, HOJE);

        assertThat(situacoes(classificadas)).containsExactly(Situacao.PROVAVEL, Situacao.NOVA, Situacao.PROVAVEL, Situacao.NOVA);
        assertThat(classificadas.get(0).parecido().manual()).isTrue();
        assertThat(classificadas.get(0).marcada()).isFalse();
        assertThat(classificadas.get(1).marcada()).isTrue();
        assertThat(classificadas.get(2).parecido().id()).isEqualTo(2);
    }

    @Test
    void oOfxDoNubankComOsDoisCafes() {
        String ofx = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>"
                + "<STMTTRN><DTPOSTED>20261105<TRNAMT>-6.00<FITID>n2<MEMO>Compra no débito - CAFE DA ESQUINA</STMTTRN>"
                + "<STMTTRN><DTPOSTED>20261105<TRNAMT>-6.00<FITID>n3<MEMO>Compra no débito - CAFE DA ESQUINA</STMTTRN>"
                + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";

        List<Classificada> classificadas = Classificador.classificar(CONTA, new LeitorOfx().ler(ofx).linhas(), List.of(),
                SALDO_INICIAL, HOJE);

        assertThat(classificadas).extracting(Classificada::chave).containsExactly("ID:11:n2", "ID:11:n3");
        assertThat(classificadas).allMatch(Classificada::marcada);
    }

    private static List<Situacao> situacoes(List<Classificada> classificadas) {
        return classificadas.stream().map(Classificada::situacao).toList();
    }

    private static Existente importado(long id, String data, String valor, String descricao, String chave) {
        return new Existente(id, LocalDate.parse(data), new BigDecimal(valor), descricao, descricao, chave, 4L, CRIADO, null);
    }

    private static List<ExtratoLido.Linha> linhas(ExtratoLido.Linha... linhas) {
        return new ArrayList<>(List.of(linhas));
    }

    /** Uma linha legível, como a dos leitores (a descrição limpa é a do arquivo, aqui já sem espaços nas pontas). */
    private static ExtratoLido.Linha linha(int numero, String data, String valor, String descricao, String identificador) {
        String limpa = descricao.strip().replaceAll("\\s+", " ");
        return new ExtratoLido.Linha(numero, LocalDate.parse(data), new BigDecimal(valor), limpa, limpa, identificador, null);
    }
}
