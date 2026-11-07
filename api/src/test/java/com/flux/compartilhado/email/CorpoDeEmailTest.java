package com.flux.compartilhado.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorpoDeEmailTest {

    @Test
    void incluiTodosOsBlocosNoHtmlENoTexto() {
        MensagemDeEmail email = CorpoDeEmail.comTitulo("Título")
                .paragrafo("Parágrafo")
                .botao("Botão", "https://flux.teste.local/botao")
                .link("Link", "https://flux.teste.local/link")
                .pequeno("Pequeno")
                .endereco("https://flux.teste.local/endereco")
                .rodape("Rodapé")
                .para("destino@teste.local", "Assunto");

        assertThat(email.html()).contains("Título", "Parágrafo", "Botão", "Link", "Pequeno",
                "https://flux.teste.local/endereco", "Rodapé");
        assertThat(email.texto()).contains("Título", "Parágrafo", "Botão: https://flux.teste.local/botao",
                "Link: https://flux.teste.local/link", "Pequeno", "https://flux.teste.local/endereco", "--\nRodapé");
    }

    @Test
    void escapaTextoEUrlNoHtml() {
        MensagemDeEmail email = CorpoDeEmail.comTitulo("Título")
                .paragrafo("<script>alert(1)</script>")
                .botao("Botão", "https://flux.teste.local/?a=1&b=2")
                .para("destino@teste.local", "Assunto");

        assertThat(email.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "href=\"https://flux.teste.local/?a=1&amp;b=2\"")
                .doesNotContain("<script>");
        assertThat(email.texto()).contains("<script>alert(1)</script>");
    }

    @Test
    void naoIncluiSeparadorDeRodapeQuandoNaoHaRodape() {
        MensagemDeEmail email = CorpoDeEmail.comTitulo("Título").para("destino@teste.local", "Assunto");

        assertThat(email.texto()).doesNotContain("--");
    }
}
