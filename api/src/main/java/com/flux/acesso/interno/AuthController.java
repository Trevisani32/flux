package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.EntrarRequest;
import com.flux.acesso.interno.dto.SessaoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_RENOVACAO = "flux_renovacao";

    private final AutenticacaoService autenticacaoService;
    private final CookieRenovacao cookieRenovacao;

    public AuthController(AutenticacaoService autenticacaoService, CookieRenovacao cookieRenovacao) {
        this.autenticacaoService = autenticacaoService;
        this.cookieRenovacao = cookieRenovacao;
    }

    @PostMapping("/entrar")
    public ResponseEntity<SessaoResponse> entrar(@Valid @RequestBody EntrarRequest request) {
        ResultadoAutenticacao resultado = autenticacaoService.entrar(request.email(), request.senha());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.criar(resultado.tokenRenovacao()).toString())
                .body(resultado.sessao());
    }

    @PostMapping("/renovar")
    public ResponseEntity<SessaoResponse> renovar(
            @CookieValue(name = COOKIE_RENOVACAO, required = false) String tokenRenovacao) {
        ResultadoAutenticacao resultado = autenticacaoService.renovar(tokenRenovacao);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.criar(resultado.tokenRenovacao()).toString())
                .body(resultado.sessao());
    }

    @PostMapping("/sair")
    public ResponseEntity<Void> sair(@CookieValue(name = COOKIE_RENOVACAO, required = false) String tokenRenovacao) {
        autenticacaoService.sair(tokenRenovacao);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRenovacao.limpar().toString())
                .build();
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ProblemDetail> trataCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", false);
    }

    @ExceptionHandler(SessaoInvalidaException.class)
    public ResponseEntity<ProblemDetail> trataSessaoInvalida(SessaoInvalidaException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.", true);
    }

    private ResponseEntity<ProblemDetail> problema(HttpStatus status, String detail, boolean limparCookie) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(status, detail);
        corpo.setTitle("Não autenticado");
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(status);
        if (limparCookie) {
            resposta.header(HttpHeaders.SET_COOKIE, cookieRenovacao.limpar().toString());
        }
        return resposta.body(corpo);
    }
}
