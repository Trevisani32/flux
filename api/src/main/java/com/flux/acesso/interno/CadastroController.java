package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.CadastrarRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só existe com APP_CADASTRO_PUBLICO=true. Desligado, o Spring MVC não acha o handler
 * e responde 404 "O recurso solicitado não existe.", como em qualquer rota inexistente.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnBooleanProperty("app.conta.cadastro-publico")
public class CadastroController {

    private final CadastroService cadastroService;

    public CadastroController(CadastroService cadastroService) {
        this.cadastroService = cadastroService;
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<Void> cadastrar(@Valid @RequestBody CadastrarRequest request, HttpServletRequest http) {
        cadastroService.cadastrar(request.nome(), request.email(), request.senha(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }
}
