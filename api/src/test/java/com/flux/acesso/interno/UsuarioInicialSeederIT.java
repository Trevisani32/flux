package com.flux.acesso.interno;

import java.time.Duration;
import java.util.UUID;

import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioInicialSeederIT extends IntegracaoBase {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void criaUsuarioNormalizadoESegundaExecucaoNaoAltera() throws Exception {
        String email = " seed-" + UUID.randomUUID() + "@teste.local ";
        UsuarioInicialSeeder seeder = new UsuarioInicialSeeder(propriedades(email, "senha-de-seed-123", "  Seed  "), usuarioService);

        seeder.run(new DefaultApplicationArguments());
        Usuario criado = usuarioRepository.findByEmail(email.strip()).orElseThrow();
        String hash = criado.getSenhaHash();
        seeder.run(new DefaultApplicationArguments());

        Usuario repetido = usuarioRepository.findByEmail(email.strip()).orElseThrow();
        assertThat(repetido.getNome()).isEqualTo("Seed");
        assertThat(repetido.getSenhaHash()).isEqualTo(hash);
        assertThat(criado.getEmailConfirmadoEm()).isNotNull();
    }

    @Test
    void naoCriaComConfiguracaoAusenteOuInvalida() {
        assertThatCode(() -> new UsuarioInicialSeeder(propriedades("", "", ""), usuarioService)
                .run(new DefaultApplicationArguments())).doesNotThrowAnyException();
        assertThatThrownBy(() -> executar("so-email@teste.local", "", "Nome"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Defina USUARIO_INICIAL_EMAIL e USUARIO_INICIAL_SENHA juntos");
        assertThatThrownBy(() -> executar("senha-curta@teste.local", "123456789", "Nome"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("A senha precisa ter entre 10 e 72 caracteres.");
        assertThatThrownBy(() -> executar("nome-longo@teste.local", "senha-de-seed-123", "a".repeat(121)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("O nome pode ter no máximo 120 caracteres.");
        assertThatThrownBy(() -> executar("sem-arroba.flux.local", "senha-de-seed-123", "Nome"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("E-mail inválido.");
        String emailLongo = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".local";
        assertThatThrownBy(() -> executar(emailLongo, "senha-de-seed-123", "Nome"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("E-mail inválido.");
    }

    @Test
    void usaNomePadraoQuandoNomeEstaEmBranco() throws Exception {
        String email = "sem-nome-" + UUID.randomUUID() + "@teste.local";
        executar(email, "senha-de-seed-123", "   ");

        assertThat(usuarioRepository.findByEmail(email).orElseThrow().getNome()).isEqualTo("Usuário");
    }

    private void executar(String email, String senha, String nome) throws Exception {
        new UsuarioInicialSeeder(propriedades(email, senha, nome), usuarioService)
                .run(new DefaultApplicationArguments());
    }

    private PropriedadesAcesso propriedades(String email, String senha, String nome) {
        return new PropriedadesAcesso(
                new PropriedadesAcesso.PropriedadesJwt("segredo-de-teste-com-mais-de-trinta-e-dois-bytes", Duration.ofMinutes(15)),
                new PropriedadesAcesso.PropriedadesRenovacao(Duration.ofDays(30), true),
                new PropriedadesAcesso.PropriedadesUsuarioInicial(email, senha, nome));
    }
}
