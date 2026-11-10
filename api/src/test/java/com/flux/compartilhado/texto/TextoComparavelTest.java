package com.flux.compartilhado.texto;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoComparavelTest {

    @Test
    void limparEspacosJuntaSequenciasETiraAsPontas() {
        assertThat(TextoComparavel.limparEspacos("  Conta   conjunta \t")).isEqualTo("Conta conjunta");
        assertThat(TextoComparavel.limparEspacos(" Poupança  nova ")).isEqualTo("Poupança nova");
        assertThat(TextoComparavel.limparEspacos("   ")).isEmpty();
    }

    @Test
    void chaveIgnoraMaiusculasAcentosEEspacos() {
        assertThat(TextoComparavel.chave("Poupança")).isEqualTo("poupanca");
        assertThat(TextoComparavel.chave("  POUPANÇA ")).isEqualTo("poupanca");
        assertThat(TextoComparavel.chave("poupanca")).isEqualTo("poupanca");
        assertThat(TextoComparavel.chave("Transporte  Público")).isEqualTo("transporte publico");
        // "é" decomposto (e + acento combinante) e composto dão a mesma chave.
        assertThat(TextoComparavel.chave("Café")).isEqualTo(TextoComparavel.chave("Café")).isEqualTo("cafe");
        assertThat(TextoComparavel.chave("İstanbul")).isEqualTo("istanbul");
        assertThat(TextoComparavel.chave("Saúde")).isNotEqualTo(TextoComparavel.chave("Saudade"));
    }

    @Test
    void ordemAlfabeticaDoPtBrNaoDiferenciaAcentosNemMaiusculas() {
        List<String> nomes = new ArrayList<>(List.of("Zeta", "alfa", "Beta", "álamo", "Conta conjunta", "Contas da casa"));
        nomes.sort(TextoComparavel.ordemAlfabetica());
        assertThat(nomes).containsExactly("álamo", "alfa", "Beta", "Conta conjunta", "Contas da casa", "Zeta");
        assertThat(TextoComparavel.ordemAlfabetica().compare("Saúde", "saude")).isZero();
    }
}
