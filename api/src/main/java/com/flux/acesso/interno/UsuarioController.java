package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.ContaResponse;
import com.flux.acesso.interno.dto.ExcluirContaRequest;
import com.flux.acesso.interno.dto.RenomearRequest;
import com.flux.acesso.interno.dto.TrocarEmailRequest;
import com.flux.acesso.interno.dto.TrocarSenhaRequest;
import com.flux.compartilhado.seguranca.UsuarioAtual;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ContaResponse me() {
        return usuarioService.conta(usuarioAtual.id());
    }

    @PutMapping("/me/nome")
    public ContaResponse renomear(@Valid @RequestBody RenomearRequest request) {
        return usuarioService.renomear(usuarioAtual.id(), request.nome());
    }

    @PostMapping("/me/email")
    public ResponseEntity<ContaResponse> trocarEmail(@Valid @RequestBody TrocarEmailRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(usuarioService.pedirTrocaDeEmail(usuarioAtual.id(), request.novoEmail(), request.senhaAtual()));
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(@Valid @RequestBody TrocarSenhaRequest request) {
        usuarioService.trocarSenha(usuarioAtual.id(), request.senhaAtual(), request.novaSenha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.limpar().toString())
                .build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> excluir(@Valid @RequestBody ExcluirContaRequest request) {
        usuarioService.excluir(usuarioAtual.id(), request.senhaAtual());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.limpar().toString())
                .build();
    }
}
