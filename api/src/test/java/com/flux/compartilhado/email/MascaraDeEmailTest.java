package com.flux.compartilhado.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MascaraDeEmailTest {

    @Test
    void mascaraEnderecoValidoPeloPrimeiroCaractere() {
        assertThat(MascaraDeEmail.mascarar("dev.novo@exemplo.local")).isEqualTo("d•••@exemplo.local");
    }

    @Test
    void mascaraEntradasSemEnderecoValido() {
        assertThat(MascaraDeEmail.mascarar("semarroba")).isEqualTo("•••");
        assertThat(MascaraDeEmail.mascarar("@x.local")).isEqualTo("•••");
        assertThat(MascaraDeEmail.mascarar(null)).isEqualTo("•••");
    }
}
