package com.flux.acesso.interno;

import com.flux.compartilhado.email.EnvioDeEmail;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Avisos por e-mail que não levam link de uso único. Rodam depois do commit. */
@Component
public class EnvioDeAvisosPorEmail {

    private final UsuarioRepository usuarioRepository;
    private final ModelosDeEmail modelos;
    private final EnvioDeEmail envioDeEmail;

    public EnvioDeAvisosPorEmail(UsuarioRepository usuarioRepository, ModelosDeEmail modelos,
                                 EnvioDeEmail envioDeEmail) {
        this.usuarioRepository = usuarioRepository;
        this.modelos = modelos;
        this.envioDeEmail = envioDeEmail;
    }

    @ApplicationModuleListener
    public void tentativaDeCadastro(TentativaDeCadastroComEmailExistente evento) {
        usuarioRepository.findById(evento.usuarioId()).ifPresent(usuario ->
                envioDeEmail.enviar(modelos.tentativaDeCadastro(usuario.getEmail(), usuario.getNome())));
    }

    @ApplicationModuleListener
    public void trocaDeEmailPedida(TrocaDeEmailPedida evento) {
        envioDeEmail.enviar(modelos.avisoDeTrocaDeEmail(evento.emailAtual(), evento.nome(), evento.emailNovo()));
    }

    @ApplicationModuleListener
    public void contaExcluida(ContaExcluida evento) {
        envioDeEmail.enviar(modelos.contaExcluida(evento.email(), evento.nome(), evento.excluidaEm()));
    }
}
