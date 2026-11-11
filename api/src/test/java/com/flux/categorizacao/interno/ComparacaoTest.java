package com.flux.categorizacao.interno;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisões 4 e 5 da 04b: a comparação das regras e as três condições. */
class ComparacaoTest {

    @Test
    void chaveIgnoraMaiusculasAcentosEspacosEPontuacao() {
        assertThat(Comparacao.chave("  Ubér  EATS ")).isEqualTo("uber eats");
        assertThat(Comparacao.chave("CAFÉ\u00a0DA ESQUINA")).isEqualTo("cafe da esquina");
        assertThat(Comparacao.chave("UBER *TRIP")).isEqualTo("uber trip");
        assertThat(Comparacao.chave("UBER* TRIP")).isEqualTo("uber trip");
        assertThat(Comparacao.chave("PAG*UBERTRIP 8812")).isEqualTo("pag ubertrip 8812");
        assertThat(Comparacao.chave("RSHOP-MERCADO BOM P-05/11")).isEqualTo("rshop mercado bom p 05 11");
        assertThat(Comparacao.chave("Transferência enviada pelo Pix - JOÃO FICTÍCIO - •••.123.456-••"))
                .isEqualTo("transferencia enviada pelo pix joao ficticio 123 456");
        assertThat(Comparacao.chave("R$ 10,00 + taxa")).isEqualTo("r 10 00 taxa");
    }

    @Test
    void textoPrecisaDeDuasLetrasOuNumeros() {
        assertThat(Comparacao.temLetrasOuNumerosSuficientes(Comparacao.chave("**"))).isFalse();
        assertThat(Comparacao.temLetrasOuNumerosSuficientes(Comparacao.chave("a *"))).isFalse();
        assertThat(Comparacao.temLetrasOuNumerosSuficientes(Comparacao.chave("a b"))).isTrue();
        assertThat(Comparacao.temLetrasOuNumerosSuficientes(Comparacao.chave("99"))).isTrue();
        assertThat(Comparacao.temLetrasOuNumerosSuficientes(Comparacao.chave("Ç"))).isFalse();
    }

    @Test
    void condicoesComparamAsChaves() {
        String alvo = Comparacao.chave("Compra no débito - UBER *TRIP HELP.UBER.COM");
        assertThat(Condicao.CONTEM.casa(alvo, Comparacao.chave("uber trip"))).isTrue();
        assertThat(Condicao.CONTEM.casa(alvo, Comparacao.chave("help.uber.com"))).isTrue();
        assertThat(Condicao.COMECA_COM.casa(alvo, Comparacao.chave("compra no debito"))).isTrue();
        assertThat(Condicao.COMECA_COM.casa(alvo, Comparacao.chave("uber"))).isFalse();
        assertThat(Condicao.IGUAL_A.casa(Comparacao.chave("TAR PACOTE SERVICOS"), Comparacao.chave("tar pacote serviços"))).isTrue();
        assertThat(Condicao.IGUAL_A.casa(Comparacao.chave("TAR PACOTE SERVICOS 11/2026"), Comparacao.chave("tar pacote servicos"))).isFalse();
        assertThat(Condicao.CONTEM.rotulo() + "|" + Condicao.COMECA_COM.rotulo() + "|" + Condicao.IGUAL_A.rotulo())
                .isEqualTo("contém|começa com|igual a");
    }
}
