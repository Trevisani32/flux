package com.flux.categorizacao.interno;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisão 21 da 04b: o texto de "Criar regra" vai até o primeiro trecho com número ou "•". */
class TextoSugeridoTest {

    @Test
    void cortaNoPrimeiroTrechoComNumeroOuMascara() {
        assertThat(TextoSugerido.de("PAG*UBERTRIP 8812")).isEqualTo("PAG*UBERTRIP");
        assertThat(TextoSugerido.de("UBER* TRIP 5521")).isEqualTo("UBER* TRIP");
        assertThat(TextoSugerido.de("PIX TRANSF JOAO F05/11")).isEqualTo("PIX TRANSF JOAO");
        assertThat(TextoSugerido.de("RSHOP-MERCADO BOM P-05/11")).isEqualTo("RSHOP-MERCADO BOM");
        assertThat(TextoSugerido.de("Transferência enviada pelo Pix - JOÃO FICTÍCIO - •••.123.456-•• - BANCO FICTICIO (0000)"))
                .isEqualTo("Transferência enviada pelo Pix - JOÃO FICTÍCIO");
        assertThat(TextoSugerido.de("SAQUE 24H 05/11 12:30")).isEqualTo("SAQUE");
        assertThat(TextoSugerido.de("DA  CONTA LUZ 12345678")).isEqualTo("DA CONTA LUZ");
        assertThat(TextoSugerido.de("IOF 05/11")).isEqualTo("IOF");
    }

    @Test
    void semNumeroOuComMenosDeTresLetrasVaiADescricaoInteira() {
        assertThat(TextoSugerido.de("Compra no débito - PADARIA CENTRAL")).isEqualTo("Compra no débito - PADARIA CENTRAL");
        assertThat(TextoSugerido.de("Pagamento de fatura")).isEqualTo("Pagamento de fatura");
        assertThat(TextoSugerido.de("99 TAXI 1234")).isEqualTo("99 TAXI 1234");
        assertThat(TextoSugerido.de("TV 2 CANAL")).isEqualTo("TV 2 CANAL");
    }
}
