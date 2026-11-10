package com.flux.compartilhado.erro;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;
import com.flux.compartilhado.limite.LimiteExcedidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        List<CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .distinct()
                .sorted(Comparator.comparing(CampoInvalido::campo).thenComparing(CampoInvalido::mensagem))
                .toList();
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        corpo.setTitle("Dados inválidos");
        corpo.setProperty("campos", campos);
        return handleExceptionInternal(ex, corpo, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        Object corpo = body;
        if (corpo == null && ex instanceof ErrorResponse respostaDeErro) {
            corpo = respostaDeErro.getBody();
        }
        if (corpo instanceof ProblemDetail problema && !(ex instanceof MethodArgumentNotValidException)) {
            definirMensagemDoFramework(problema, statusCode);
        }
        return super.handleExceptionInternal(ex, corpo, headers, statusCode, request);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> trataRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return resposta(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ProblemDetail> trataRegraDeNegocio(RegraDeNegocioException ex) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        corpo.setTitle("Operação não permitida");
        if (ex.getCodigo() != null) {
            corpo.setProperty("codigo", ex.getCodigo());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(corpo);
    }

    @ExceptionHandler(CampoInvalidoException.class)
    public ResponseEntity<ProblemDetail> trataCampoInvalido(CampoInvalidoException ex) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        corpo.setTitle("Dados inválidos");
        corpo.setProperty("campos", List.of(new CampoInvalido(ex.getCampo(), ex.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<ProblemDetail> trataLimiteExcedido(LimiteExcedidoException ex) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente de novo em alguns minutos.");
        corpo.setTitle("Muitas tentativas");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.segundosParaTentarDeNovo()))
                .body(corpo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> trataExcecao(Exception ex) {
        LOGGER.error("Erro interno não tratado", ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado.");
    }

    private ResponseEntity<ProblemDetail> resposta(HttpStatus status, String title, String detail) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(status, detail);
        corpo.setTitle(title);
        return ResponseEntity.status(status).body(corpo);
    }

    private void definirMensagemDoFramework(ProblemDetail corpo, HttpStatusCode statusCode) {
        switch (statusCode.value()) {
            case 400 -> definirMensagem(corpo, "Requisição inválida", "A requisição não pôde ser processada.");
            case 401 -> definirMensagem(corpo, "Não autenticado", "Autenticação necessária.");
            case 403 -> definirMensagem(corpo, "Acesso negado", "Você não tem permissão para acessar este recurso.");
            case 404 -> definirMensagem(corpo, "Recurso não encontrado", "O recurso solicitado não existe.");
            case 405 -> definirMensagem(corpo, "Método não permitido", "Método HTTP não suportado para este recurso.");
            case 415 -> definirMensagem(corpo, "Tipo de conteúdo não suportado", "Envie o corpo como application/json.");
            default -> definirMensagem(corpo, "Erro na requisição", "A requisição não pôde ser processada.");
        }
    }

    private void definirMensagem(ProblemDetail corpo, String title, String detail) {
        corpo.setTitle(title);
        corpo.setDetail(detail);
    }
}
