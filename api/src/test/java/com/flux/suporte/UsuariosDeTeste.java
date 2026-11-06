package com.flux.suporte;

import java.util.UUID;

import com.flux.acesso.interno.TokenAcessoEmitido;
import com.flux.acesso.interno.TokenAcessoService;
import com.flux.acesso.interno.Usuario;
import com.flux.acesso.interno.UsuarioService;

public class UsuariosDeTeste {

    private final UsuarioService usuarioService;
    private final TokenAcessoService tokenAcessoService;

    public UsuariosDeTeste(UsuarioService usuarioService, TokenAcessoService tokenAcessoService) {
        this.usuarioService = usuarioService;
        this.tokenAcessoService = tokenAcessoService;
    }

    public UsuarioDeTeste criar() {
        String email = "usuario-" + UUID.randomUUID() + "@teste.local";
        String senha = "senha-de-teste-123";
        Usuario usuario = usuarioService.criar("Usuário de Teste", email, senha);
        TokenAcessoEmitido tokenAcesso = tokenAcessoService.emitir(usuario.getId());
        return new UsuarioDeTeste(usuario.getId(), email, senha, tokenAcesso.valor());
    }

    public record UsuarioDeTeste(Long id, String email, String senha, String tokenAcesso) {
    }
}
