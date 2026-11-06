package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.TrocarSenhaRequest;
import com.flux.acesso.interno.dto.UsuarioResponse;
import com.flux.compartilhado.seguranca.UsuarioAtual;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioAtual usuarioAtual;
    private final UsuarioService usuarioService;
    private final CookieRenovacao cookieRenovacao;

    public UsuarioController(UsuarioAtual usuarioAtual, UsuarioService usuarioService,
                             CookieRenovacao cookieRenovacao) {
        this.usuarioAtual = usuarioAtual;
        this.usuarioService = usuarioService;
        this.cookieRenovacao = cookieRenovacao;
    }

    @GetMapping("/me")
    public UsuarioResponse me() {
        return usuarioService.dadosDoUsuario(usuarioAtual.id());
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocarSenhaRequest request) {
        usuarioService.trocarSenha(usuarioAtual.id(), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.limpar().toString())
                .build();
    }
}
