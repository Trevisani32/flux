package com.flux.exportacao.interno;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArquivoCsvTest {

    @Test
    void osCamposSeguemOExcelEmPortugues() {
        assertThat(ArquivoCsv.valor(new BigDecimal("-1850.00"))).isEqualTo("-1850,00");
        assertThat(ArquivoCsv.valor(new BigDecimal("8200.00"))).isEqualTo("8200,00");
        assertThat(ArquivoCsv.valor(new BigDecimal("0.05"))).isEqualTo("0,05");
        assertThat(ArquivoCsv.valor(new BigDecimal("1234567.89"))).isEqualTo("1234567,89");
        assertThat(ArquivoCsv.dia(LocalDate.of(2025, 12, 1))).isEqualTo("01/12/2025");
        assertThat(ArquivoCsv.dia(null)).isEmpty();
        assertThat(ArquivoCsv.tipo("RECEITA")).isEqualTo("Receita");
        assertThat(ArquivoCsv.tipo("DESPESA")).isEqualTo("Despesa");
        assertThat(ArquivoCsv.tipo("TRANSFERENCIA")).isEqualTo("Transferência");
        assertThat(ArquivoCsv.tipo("PAGAMENTO_FATURA")).isEqualTo("Pagamento de fatura");
        assertThat(ArquivoCsv.origem("MANUAL")).isEqualTo("Manual");
        assertThat(ArquivoCsv.origem("IMPORTADO")).isEqualTo("Importado");
        assertThat(ArquivoCsv.parcela(1, 3)).isEqualTo("1 de 3");
        assertThat(ArquivoCsv.parcela(1, 1)).isEmpty();
        assertThat(ArquivoCsv.parcela(null, null)).isEmpty();
        assertThat(ArquivoCsv.CABECALHO).isEqualTo(
                "Data;Descrição;Valor;Tipo;Categoria;Subcategoria;Conta;Cartão;Parcela;Data da compra;Observação;Origem;Descrição do extrato");
    }

    @Test
    void textoQueComecaComoFormulaGanhaApostrofo() {
        assertThat(ArquivoCsv.texto("=SOMA promoção")).isEqualTo("'=SOMA promoção");
        assertThat(ArquivoCsv.texto("+55 11")).isEqualTo("'+55 11");
        assertThat(ArquivoCsv.texto("-10% off")).isEqualTo("'-10% off");
        assertThat(ArquivoCsv.texto("@casa")).isEqualTo("'@casa");
        assertThat(ArquivoCsv.texto("\tx")).isEqualTo("'\tx");
        assertThat(ArquivoCsv.texto("\rx")).isEqualTo("\"'\rx\"");
        assertThat(ArquivoCsv.texto("Café")).isEqualTo("Café");
        assertThat(ArquivoCsv.texto("10 de março")).isEqualTo("10 de março");
        assertThat(ArquivoCsv.texto("a=b")).isEqualTo("a=b");
        assertThat(ArquivoCsv.texto(null)).isEmpty();
        assertThat(ArquivoCsv.texto("")).isEmpty();
    }

    @Test
    void aspasSoComSeparadorAspasOuQuebra() {
        assertThat(ArquivoCsv.texto("Café; pão")).isEqualTo("\"Café; pão\"");
        assertThat(ArquivoCsv.texto("Disse \"oi\"")).isEqualTo("\"Disse \"\"oi\"\"\"");
        assertThat(ArquivoCsv.texto("linha 1\nlinha 2")).isEqualTo("\"linha 1\nlinha 2\"");
        assertThat(ArquivoCsv.texto("a\rb")).isEqualTo("\"a\rb\"");
        assertThat(ArquivoCsv.texto("=A;B")).isEqualTo("\"'=A;B\"");
        assertThat(ArquivoCsv.texto("Mercado Bom Preço")).isEqualTo("Mercado Bom Preço");
    }
}
