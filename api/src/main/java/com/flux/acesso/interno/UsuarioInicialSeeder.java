package com.flux.acesso.interno;

import com.flux.compartilhado.erro.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class UsuarioInicialSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsuarioInicialSeeder.class);

    private final PropriedadesAcesso propriedades;
    private final UsuarioService usuarioService;

    public UsuarioInicialSeeder(PropriedadesAcesso propriedades, UsuarioService usuarioService) {
        this.propriedades = propriedades;
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        PropriedadesAcesso.PropriedadesUsuarioInicial usuarioInicial = propriedades.usuarioInicial();
        boolean temEmail = !emBranco(usuarioInicial.email());
        boolean temSenha = !emBranco(usuarioInicial.senha());
        if (!temEmail && !temSenha) {
            return;
        }
        if (temEmail != temSenha) {
            throw new IllegalStateException("Defina USUARIO_INICIAL_EMAIL e USUARIO_INICIAL_SENHA juntos");
        }

        String email = UsuarioService.normalizarEmail(usuarioInicial.email());
        if (usuarioService.existeEmail(email)) {
            return;
        }
        try {
            usuarioService.criar(usuarioInicial.nome(), email, usuarioInicial.senha());
            LOGGER.info("Usuário inicial criado: {}", email);
        } catch (RegraDeNegocioException ex) {
            throw new IllegalStateException("Usuário inicial inválido (USUARIO_INICIAL_*): " + ex.getMessage(), ex);
        }
    }

    private boolean emBranco(String valor) {
        return valor == null || valor.isBlank();
    }
}
