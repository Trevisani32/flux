package com.flux.exportacao.interno;

import java.io.IOException;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.exportacao.interno.dto.ExportarRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/minha-conta/exportacao")
public class ExportacaoController {

    private final UsuarioAtual usuarioAtual;
    private final ExportacaoService exportacaoService;

    public ExportacaoController(UsuarioAtual usuarioAtual, ExportacaoService exportacaoService) {
        this.usuarioAtual = usuarioAtual;
        this.exportacaoService = exportacaoService;
    }

    /**
     * A senha vem antes de qualquer cabeçalho: o 400, o 422 e o 429 saem como ProblemDetail, sem arquivo. Depois, o arquivo
     * vai direto para a resposta, aos poucos e sem Content-Length (em pedaços).
     */
    @PostMapping
    public void exportar(@Valid @RequestBody ExportarRequest pedido, HttpServletResponse resposta) throws IOException {
        Long usuarioId = usuarioAtual.id();
        exportacaoService.conferirSenha(usuarioId, pedido.senhaAtual());
        resposta.setContentType(pedido.formato() == Formato.JSON ? "application/json;charset=UTF-8" : "text/csv;charset=UTF-8");
        resposta.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(exportacaoService.nomeDoArquivo(pedido.formato())).build().toString());
        exportacaoService.escrever(usuarioId, pedido.formato(), resposta.getOutputStream());
    }
}
