package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.ConfirmacaoDeEmailResponse;
import com.flux.acesso.interno.dto.ConfirmarEmailRequest;
import com.flux.acesso.interno.dto.EmailRequest;
import com.flux.acesso.interno.dto.RedefinirSenhaRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ContaPublicaController {

    private final CadastroService cadastroService;
    private final CookieRenovacao cookieRenovacao;

    public ContaPublicaController(CadastroService cadastroService, CookieRenovacao cookieRenovacao) {
        this.cadastroService = cadastroService;
        this.cookieRenovacao = cookieRenovacao;
    }

    @PostMapping("/confirmar-email")
    public ConfirmacaoDeEmailResponse confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest request) {
        return cadastroService.confirmarEmail(request.token());
    }

    @PostMapping("/reenviar-confirmacao")
    public ResponseEntity<Void> reenviarConfirmacao(@Valid @RequestBody EmailRequest request,
                                                    HttpServletRequest http) {
        cadastroService.reenviarConfirmacao(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/esqueci-a-senha")
    public ResponseEntity<Void> esqueciASenha(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        cadastroService.esqueciASenha(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request,
                                               HttpServletRequest http) {
        cadastroService.redefinirSenha(request.token(), request.novaSenha(), http.getRemoteAddr());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.limpar().toString())
                .build();
    }

    @ExceptionHandler(LinkInvalidoException.class)
    public ResponseEntity<ProblemDetail> trataLinkInvalido(LinkInvalidoException ex) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Este link expirou ou já foi usado.");
        corpo.setTitle("Operação não permitida");
        corpo.setProperty("codigo", "LINK_INVALIDO");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(corpo);
    }
}
