package com.flux.importacao.interno.leitura;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/** Decisões 6 a 10: charset, OFX 1.x e 2.x, CSV e as linhas ilegíveis, com arquivos fictícios. */
class LeitoresTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Test
    void charsetPeloBomOuPeloUtf8EstritoEOCabecalhoDoOfxNaoManda() throws IOException {
        byte[] nubank = fixture("nubank-conta.ofx");
        assertThat(new String(nubank, StandardCharsets.UTF_8)).contains("CHARSET:1252");
        assertThat(TextoDoArquivo.decodificar(nubank)).contains("JOÃO FICTÍCIO");
        assertThat(TextoDoArquivo.decodificar("Descrição;Pão".getBytes(WINDOWS_1252))).isEqualTo("Descrição;Pão");
        byte[] comBom = concatenar(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "Data,Ação".getBytes(StandardCharsets.UTF_8));
        assertThat(TextoDoArquivo.decodificar(comBom)).isEqualTo("Data,Ação");
        byte[] utf16 = concatenar(new byte[] {(byte) 0xFF, (byte) 0xFE}, "Data;Ação".getBytes(StandardCharsets.UTF_16LE));
        assertThat(TextoDoArquivo.decodificar(utf16)).isEqualTo("Data;Ação");
        assertThat(TextoDoArquivo.binario(utf16)).isFalse();
        assertThat(TextoDoArquivo.binario("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(TextoDoArquivo.binario(new byte[] {'P', 'K', 3, 4, 0})).isTrue();
        assertThat(TextoDoArquivo.binario(new byte[] {'a', 0, 'b'})).isTrue();
    }

    @Test
    void ofxSgmlNoEstiloDoNubank() throws IOException {
        ExtratoLido extrato = new LeitorOfx().ler(TextoDoArquivo.decodificar(fixture("nubank-conta.ofx")));

        assertThat(extrato.formato()).isEqualTo(ExtratoLido.Formato.OFX);
        assertThat(extrato.bancoId()).isEqualTo("0260");
        assertThat(extrato.contaDoBanco()).isEqualTo("99999999-9");
        assertThat(extrato.contas()).isEqualTo(1);
        assertThat(extrato.cartao()).isFalse();
        assertThat(extrato.linhas()).extracting(ExtratoLido.Linha::numero, ExtratoLido.Linha::data, ExtratoLido.Linha::valor,
                        ExtratoLido.Linha::descricao)
                .containsExactly(
                        tuple(1, LocalDate.of(2026, 11, 3), new BigDecimal("-150.00"),
                                "Transferência enviada pelo Pix - JOÃO FICTÍCIO"),
                        tuple(2, LocalDate.of(2026, 11, 5), new BigDecimal("-6.00"),
                                "Compra no débito - CAFE DA ESQUINA"),
                        tuple(3, LocalDate.of(2026, 11, 5), new BigDecimal("-6.00"),
                                "Compra no débito - CAFE DA ESQUINA"),
                        tuple(4, LocalDate.of(2026, 11, 5), new BigDecimal("7200.00"),
                                "Transferência recebida - EMPRESA FICTÍCIA LTDA"),
                        tuple(5, LocalDate.of(2026, 11, 6), new BigDecimal("0.00"), "IOF ADICIONAL"));
        assertThat(extrato.linhas().get(1).identificador()).isEqualTo("0f1e2d3c-0000-4000-8000-000000000002");
    }

    @Test
    void ofxNoEstiloDoItauEmWindows1252ComTagsFechadasFusoEstEVirgula() {
        String ofx = "OFXHEADER:100\r\nDATA:OFXSGML\r\nVERSION:102\r\nCHARSET:1252\r\n\r\n<OFX>\n<BANKMSGSRSV1>\n<STMTTRNRS>\n<STMTRS>\n"
                + "<BANKACCTFROM>\n<BANKID>0341</BANKID>\n<ACCTID>1234567890</ACCTID>\n</BANKACCTFROM>\n<BANKTRANLIST>\n"
                + "<STMTTRN>\n<TRNTYPE>DEBIT</TRNTYPE>\n<DTPOSTED>20261103100000[-03:EST]</DTPOSTED>\n<TRNAMT>-50,00</TRNAMT>\n"
                + "<FITID>20261103001</FITID>\n<MEMO>TAR PACOTE SERVIÇOS</MEMO>\n</STMTTRN>\n"
                + "<STMTTRN>\n<TRNTYPE>DEBIT</TRNTYPE>\n<DTPOSTED>20261103100000[-03:EST]</DTPOSTED>\n<TRNAMT>-1.234,56</TRNAMT>\n"
                + "<FITID>20261103002</FITID>\n<MEMO>PAGTO ELETRON COBRANCA</MEMO>\n</STMTTRN>\n"
                + "<STMTTRN>\n<DTPOSTED>2026-11-04</DTPOSTED>\n<TRNAMT>-1.00</TRNAMT>\n<MEMO>DATA ERRADA</MEMO>\n</STMTTRN>\n"
                + "<STMTTRN>\n<DTPOSTED>20261104</DTPOSTED>\n<TRNAMT>-1.005</TRNAMT>\n<MEMO>TRES CASAS</MEMO>\n</STMTTRN>\n"
                + "</BANKTRANLIST>\n</STMTRS>\n</STMTTRNRS>\n</BANKMSGSRSV1>\n</OFX>\n";

        ExtratoLido extrato = new LeitorOfx().ler(TextoDoArquivo.decodificar(ofx.getBytes(WINDOWS_1252)));

        assertThat(extrato.bancoId()).isEqualTo("0341");
        assertThat(extrato.linhas()).hasSize(4);
        assertThat(extrato.linhas().get(0).data()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(extrato.linhas().get(0).valor()).isEqualByComparingTo("-50.00");
        assertThat(extrato.linhas().get(0).descricao()).isEqualTo("TAR PACOTE SERVIÇOS");
        assertThat(extrato.linhas().get(1).valor()).isEqualByComparingTo("-1234.56");
        assertThat(extrato.linhas().get(2).erro()).isEqualTo("data ilegível");
        assertThat(extrato.linhas().get(3).erro()).isEqualTo("valor ilegível");
    }

    @Test
    void ofx2EmXmlComBomEntidadeENameQuandoNaoHaMemo() throws IOException {
        byte[] comBom = concatenar(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, fixture("extrato-2x.ofx"));
        String texto = TextoDoArquivo.decodificar(comBom);
        assertThat(TextoDoArquivo.ofx(texto)).isTrue();

        ExtratoLido extrato = new LeitorOfx().ler(texto);

        assertThat(extrato.linhas()).extracting(ExtratoLido.Linha::descricao)
                .containsExactly("P&G SUPERMERCADO", "Pagamento de boleto - Escola Futuro");
        assertThat(extrato.linhas().get(1).data()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void cartaoEDuasContasSaoReconhecidos() throws IOException {
        ExtratoLido cartao = new LeitorOfx().ler(TextoDoArquivo.decodificar(fixture("cartao.ofx")));
        assertThat(cartao.cartao()).isTrue();
        assertThat(cartao.contas()).isZero();

        String duas = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKACCTFROM><BANKID>0341<ACCTID>111</BANKACCTFROM><BANKTRANLIST>"
                + "<STMTTRN><DTPOSTED>20261103<TRNAMT>-1.00<FITID>a<MEMO>A</STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS>"
                + "<STMTTRNRS><STMTRS><BANKACCTFROM><BANKID>0341<ACCTID>222</BANKACCTFROM><BANKTRANLIST>"
                + "<STMTTRN><DTPOSTED>20261103<TRNAMT>-2.00<FITID>b<MEMO>B</STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
        assertThat(new LeitorOfx().ler(duas).contas()).isEqualTo(2);
    }

    @Test
    void descricaoLongaEVaziaEIdentificadorGrandeDemais() {
        String ofx = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>"
                + "<STMTTRN><DTPOSTED>20261103<TRNAMT>-1.00<FITID>" + "9".repeat(256) + "<MEMO>" + "TEXTO   ".repeat(60) + "</STMTTRN>"
                + "<STMTTRN><DTPOSTED>20261103<TRNAMT>-2.00<FITID>b<MEMO>   </STMTTRN>"
                + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";

        List<ExtratoLido.Linha> linhas = new LeitorOfx().ler(ofx).linhas();

        assertThat(linhas.get(0).descricao()).hasSize(255).startsWith("TEXTO TEXTO");
        assertThat(linhas.get(0).descricaoCompleta()).hasSize(60 * 6 - 1);
        assertThat(linhas.get(0).identificador()).isNull();
        assertThat(linhas.get(1).descricao()).isEqualTo("Sem descrição");
    }

    @Test
    void csvDoNubankComOPerfilPronto() throws IOException {
        String texto = TextoDoArquivo.decodificar(fixture("nubank-conta.csv"));
        assertThat(TextoDoArquivo.cabecalhoDoNubank(texto)).isTrue();
        assertThat(TextoDoArquivo.faturaDoNubank("date,title,amount\n2026-11-03,Streaming,39.90\n")).isTrue();

        ExtratoLido extrato = new LeitorCsv(FormatoCsv.NUBANK_CONTA).ler(texto);

        assertThat(extrato.formato()).isEqualTo(ExtratoLido.Formato.CSV);
        assertThat(extrato.linhas()).extracting(ExtratoLido.Linha::numero).containsExactly(2, 3, 4, 5);
        assertThat(extrato.linhas().get(0).valor()).isEqualByComparingTo("-18.50");
        assertThat(extrato.linhas().get(0).descricao()).isEqualTo("Compra no débito - PADARIA CENTRAL");
        assertThat(extrato.linhas().get(0).identificador()).isEqualTo("6a1f0000-0000-4000-8000-0000000000a1");
        assertThat(extrato.linhas().get(3).valor()).isEqualByComparingTo("7200.00");
        // Vírgula decimal num perfil de ponto: ilegível, e não -600,00.
        assertThat(new LeitorCsv(FormatoCsv.NUBANK_CONTA).ler("Data,Valor,Identificador,Descrição\n05/11/2026,\"-6,00\",x1,CAFE\n")
                .linhas().getFirst().erro()).isEqualTo("valor ilegível");
    }

    @Test
    void csvGenericoComAspasMilharVirgulaELinhaIlegivel() {
        FormatoCsv formato = new FormatoCsv(FormatoCsv.Separador.PONTO_E_VIRGULA, 1, 1, FormatoCsv.FormatoData.DD_MM_AAAA, 2, 3,
                null, null, FormatoCsv.SeparadorDecimal.VIRGULA);
        String csv = "Data;Descrição;Valor\r\n02/11/2026;\"Pão de Açúcar; loja 12\";-87,40\r\n\r\n03/11/2026;ALUGUEL;-1.234,56\r\n"
                + "31/02/2026;DATA QUE NÃO EXISTE;-1,00\r\n04/11/2026;SALÁRIO;R$ 7.200,00\r\n05/11/2026;SEM VALOR;\r\n";

        List<ExtratoLido.Linha> linhas = new LeitorCsv(formato).ler(TextoDoArquivo.decodificar(csv.getBytes(WINDOWS_1252))).linhas();

        assertThat(linhas).hasSize(5);
        assertThat(linhas.get(0).descricao()).isEqualTo("Pão de Açúcar; loja 12");
        assertThat(linhas.get(0).valor()).isEqualByComparingTo("-87.40");
        assertThat(linhas.get(1).valor()).isEqualByComparingTo("-1234.56");
        assertThat(linhas.get(2).erro()).isEqualTo("data ilegível");
        assertThat(linhas.get(3).valor()).isEqualByComparingTo("7200.00");
        assertThat(linhas.get(4).erro()).isEqualTo("valor ilegível");
        // Ponto decimal num perfil de vírgula: ilegível, e não -600,00.
        assertThat(new LeitorCsv(formato).ler("Data;Descrição;Valor\n05/11/2026;CAFE;-6.00\n").linhas().getFirst().erro())
                .isEqualTo("valor ilegível");
    }

    @Test
    void csvComEntradasESaidasEmColunasSeparadas() {
        FormatoCsv formato = new FormatoCsv(FormatoCsv.Separador.VIRGULA, 0, 1, FormatoCsv.FormatoData.AAAA_MM_DD, 2, 3, 4, null,
                FormatoCsv.SeparadorDecimal.PONTO);
        String csv = "2026-11-02,SALARIO,7200.00,\n2026-11-03,MERCADO,,120.50\n2026-11-04,TARIFA,,-9.90\n2026-11-05,NADA,,\n";

        List<ExtratoLido.Linha> linhas = new LeitorCsv(formato).ler(csv).linhas();

        assertThat(linhas).extracting(ExtratoLido.Linha::valor)
                .containsExactly(new BigDecimal("7200.00"), new BigDecimal("-120.50"), new BigDecimal("-9.90"), null);
        assertThat(linhas.get(3).erro()).isEqualTo("valor ilegível");
    }

    /** O teto do lançamento manual: acima dele, a linha é ilegível (e não um 500 na confirmação). */
    @Test
    void valorAcimaDoTetoDoLancamentoEIlegivel() {
        FormatoCsv formato = new FormatoCsv(FormatoCsv.Separador.VIRGULA, 0, 1, FormatoCsv.FormatoData.AAAA_MM_DD, 2, 3, null, null,
                FormatoCsv.SeparadorDecimal.PONTO);
        String csv = "2026-11-02,NO TETO,-9999999999999.99\n2026-11-03,ACIMA,10000000000000.00\n"
                + "2026-11-04,IDENTIFICADOR NUMERICO,123456789012345678901\n";

        List<ExtratoLido.Linha> linhas = new LeitorCsv(formato).ler(csv).linhas();

        assertThat(linhas.get(0).valor()).isEqualByComparingTo("-9999999999999.99");
        assertThat(linhas).extracting(ExtratoLido.Linha::erro).containsExactly(null, "valor ilegível", "valor ilegível");
        assertThat(LeitorOfx.valor("-10000000000000,00")).isNull();
        assertThat(LeitorOfx.valor("9999999999999.99")).isEqualByComparingTo("9999999999999.99");
    }

    private static byte[] fixture(String nome) throws IOException {
        try (InputStream entrada = LeitoresTest.class.getResourceAsStream("/fixtures/" + nome)) {
            return entrada.readAllBytes();
        }
    }

    private static byte[] concatenar(byte[] inicio, byte[] resto) {
        byte[] todos = new byte[inicio.length + resto.length];
        System.arraycopy(inicio, 0, todos, 0, inicio.length);
        System.arraycopy(resto, 0, todos, inicio.length, resto.length);
        return todos;
    }
}
