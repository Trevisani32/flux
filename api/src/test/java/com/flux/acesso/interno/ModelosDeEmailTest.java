package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelosDeEmailTest {

    private final ModelosDeEmail modelos = new ModelosDeEmail(propriedades("privacidade@teste.local"),
            Clock.fixed(Instant.parse("2026-11-07T01:40:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void usaOsSeisAssuntosAprovados() {
        assertThat(modelos.confirmacaoDeCadastro("ana@teste.local", "Ana", "abc").assunto())
                .isEqualTo("Confirme seu e-mail no Flux");
        assertThat(modelos.redefinicaoDeSenha("ana@teste.local", "Ana", "abc").assunto())
                .isEqualTo("Redefinir sua senha do Flux");
        assertThat(modelos.confirmacaoDeNovoEmail("ana@teste.local", "Ana", "abc").assunto())
                .isEqualTo("Confirme seu novo e-mail no Flux");
        assertThat(modelos.tentativaDeCadastro("ana@teste.local", "Ana").assunto())
                .isEqualTo("Tentativa de cadastro com o seu e-mail");
        assertThat(modelos.avisoDeTrocaDeEmail("ana@teste.local", "Ana", "dev@exemplo.local").assunto())
                .isEqualTo("Pedido de troca do e-mail da sua conta");
        assertThat(modelos.contaExcluida("ana@teste.local", "Ana", Instant.parse("2026-11-07T01:40:00Z")).assunto())
                .isEqualTo("Sua conta do Flux foi excluída");
    }

    @Test
    void usaPrimeiroNomeNoCumprimento() {
        assertThat(modelos.confirmacaoDeCadastro("ana@teste.local", "  Ana Souza ", "abc").texto())
                .contains("Olá, Ana.");
    }

    @Test
    void montaLinkDeConfirmacaoNoTextoENoHref() {
        var email = modelos.confirmacaoDeCadastro("ana@teste.local", "Ana", "abc");
        assertThat(email.texto()).contains("http://localhost:4200/confirmar-email#token=abc");
        assertThat(email.html()).contains("href=\"http://localhost:4200/confirmar-email#token=abc\"");
    }

    @Test
    void montaLinkDeRedefinicao() {
        assertThat(modelos.redefinicaoDeSenha("ana@teste.local", "Ana", "abc").texto())
                .contains("/redefinir-senha#token=abc");
    }

    @Test
    void mascaraNovoEnderecoNoAviso() {
        assertThat(modelos.avisoDeTrocaDeEmail("ana@teste.local", "Ana", "dev@exemplo.local").texto())
                .contains("d•••@exemplo.local");
    }

    @Test
    void informaDataHoraEContatoNaExclusao() {
        assertThat(modelos.contaExcluida("ana@teste.local", "Ana", Instant.parse("2026-11-07T01:40:00Z")).texto())
                .contains("apagados em 06/11/2026 às 22:40.", "Se não foi você, fale com privacidade@teste.local.");
    }

    @Test
    void omiteContatoVazioNaExclusao() {
        ModelosDeEmail semContato = new ModelosDeEmail(propriedades(""),
                Clock.fixed(Instant.parse("2026-11-07T01:40:00Z"), ZoneId.of("America/Sao_Paulo")));
        assertThat(semContato.contaExcluida("ana@teste.local", "Ana", Instant.parse("2026-11-07T01:40:00Z")).texto())
                .doesNotContain("Se não foi você");
    }

    @Test
    void aceitaApenasPrimeiroNomeSeguro() {
        assertThat(ModelosDeEmail.primeiroNome("  Ana Souza ")).isEqualTo("Ana");
        assertThat(ModelosDeEmail.primeiroNome("D'Ávila Souza")).isEqualTo("D'Ávila");
        assertThat(ModelosDeEmail.primeiroNome("D’Ávila")).isEqualTo("D’Ávila");
        assertThat(ModelosDeEmail.primeiroNome("Ana-Maria")).isEqualTo("Ana-Maria");
        assertThat(ModelosDeEmail.primeiroNome("www.golpe.example/x")).isEqualTo("Usuário");
        assertThat(ModelosDeEmail.primeiroNome("Ana2")).isEqualTo("Usuário");
        assertThat(ModelosDeEmail.primeiroNome("a".repeat(41))).isEqualTo("Usuário");
        assertThat(ModelosDeEmail.primeiroNome("   ")).isEqualTo("Usuário");
        assertThat(ModelosDeEmail.primeiroNome(null)).isEqualTo("Usuário");
    }

    @Test
    void naoReplicaNomeMaliciosoNoEmailDeCadastro() {
        var email = modelos.confirmacaoDeCadastro("ana@teste.local", "www.golpe.example/entrar Souza", "abc");
        assertThat(email.texto()).contains("Olá, Usuário.").doesNotContain("golpe");
        assertThat(email.html()).contains("Olá, Usuário.").doesNotContain("golpe");
    }

    @Test
    void naoReplicaNomeMaliciosoNoEmailDoNovoEndereco() {
        var email = modelos.confirmacaoDeNovoEmail("ana@teste.local", "http://golpe.example", "abc");
        assertThat(email.texto()).contains("Olá, Usuário.").doesNotContain("golpe");
        assertThat(email.html()).contains("Olá, Usuário.").doesNotContain("golpe");
    }

    private static PropriedadesConta propriedades(String contato) {
        return new PropriedadesConta("http://localhost:4200", false,
                new PropriedadesConta.PropriedadesControlador("Responsável", contato), true);
    }
}
