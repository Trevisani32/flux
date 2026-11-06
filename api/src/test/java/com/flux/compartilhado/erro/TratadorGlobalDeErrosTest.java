package com.flux.compartilhado.erro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = TratadorGlobalDeErrosTest.ControladorDeTeste.class)
@Import(TratadorGlobalDeErrosTest.ControladorDeTeste.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TratadorGlobalDeErrosTest {

    @Autowired
    private org.springframework.test.web.servlet.assertj.MockMvcTester mvc;

    @Test
    void retornaCamposOrdenadosParaValidacao() {
        assertThat(mvc.post().uri("/teste/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"\"}"))
                .hasStatus(400)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("title").isEqualTo("Dados inválidos");
        assertThat(mvc.post().uri("/teste/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"\"}"))
                .bodyJson().extractingPath("campos.length()").isEqualTo(2);
        assertThat(mvc.post().uri("/teste/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"\"}"))
                .bodyJson().extractingPath("campos[0].campo").isEqualTo("email");
        assertThat(mvc.post().uri("/teste/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"email\":\"\"}"))
                .bodyJson().extractingPath("campos[1].campo").isEqualTo("nome");
    }

    @Test
    void removeCamposRepetidos() {
        assertThat(mvc.post().uri("/teste/repetido").contentType(MediaType.APPLICATION_JSON)
                .content("{\"campo\":\"\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("campos.length()").isEqualTo(1);
    }

    @Test
    void trataJsonMalformado() {
        assertThat(mvc.post().uri("/teste/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":"))
                .hasStatus(400)
                .bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
    }

    @Test
    void trataExcecoesDaAplicacaoERotaInexistente() {
        assertThat(mvc.get().uri("/teste/nao-encontrado")).hasStatus(404)
                .bodyJson().extractingPath("detail").isEqualTo("Não encontrado.");
        assertThat(mvc.get().uri("/teste/regra")).hasStatus(422)
                .bodyJson().extractingPath("title").isEqualTo("Operação não permitida");
        assertThat(mvc.get().uri("/rota-inexistente")).hasStatus(404)
                .bodyJson().extractingPath("title").isEqualTo("Recurso não encontrado");
        assertThat(mvc.get().uri("/rota-inexistente"))
                .bodyJson().extractingPath("detail").isEqualTo("O recurso solicitado não existe.");
    }

    @Test
    void traduzErrosDoFramework() {
        assertThat(mvc.get().uri("/teste/validar")).hasStatus(405)
                .bodyJson().extractingPath("title").isEqualTo("Método não permitido");
        assertThat(mvc.get().uri("/teste/validar"))
                .bodyJson().extractingPath("detail").isEqualTo("Método HTTP não suportado para este recurso.");
        assertThat(mvc.post().uri("/teste/validar").contentType(MediaType.TEXT_PLAIN)
                .content("texto"))
                .hasStatus(415)
                .bodyJson().extractingPath("title").isEqualTo("Tipo de conteúdo não suportado");
    }

    @Test
    void trataErroInternoSemExporStackTrace() throws Exception {
        org.springframework.test.web.servlet.assertj.MvcTestResult resultado = mvc.get().uri("/teste/erro").exchange();

        assertThat(resultado).hasStatus(500).bodyJson().extractingPath("title").isEqualTo("Erro interno");
        assertThat(resultado).bodyJson().extractingPath("detail").isEqualTo("Ocorreu um erro inesperado.");
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("erro de teste", "IllegalStateException");
    }

    @RestController
    @RequestMapping("/teste")
    public static class ControladorDeTeste {

        @PostMapping("/validar")
        public CorpoValido validar(@Valid @RequestBody CorpoValido corpo) {
            return corpo;
        }

        @PostMapping("/repetido")
        public CorpoRepetido repetir(@Valid @RequestBody CorpoRepetido corpo) {
            return corpo;
        }

        @GetMapping("/nao-encontrado")
        public void naoEncontrado() {
            throw new RecursoNaoEncontradoException("Não encontrado.");
        }

        @GetMapping("/regra")
        public void regra() {
            throw new RegraDeNegocioException("Regra inválida.");
        }

        @GetMapping("/erro")
        public void erro() {
            throw new IllegalStateException("erro de teste");
        }
    }

    public record CorpoValido(
            @NotBlank(message = "Nome obrigatório.") String nome,
            @NotBlank(message = "E-mail obrigatório.") String email) {
    }

    public record CorpoRepetido(
            @NotBlank(message = "Campo obrigatório.")
            @Size(min = 3, message = "Campo obrigatório.") String campo) {
    }
}
