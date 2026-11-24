package com.flux.relatorios.interno;

import java.time.YearMonth;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.relatorios.interno.dto.RelatorioDoMesResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final UsuarioAtual usuarioAtual;
    private final RelatorioService relatorioService;

    public RelatorioController(UsuarioAtual usuarioAtual, RelatorioService relatorioService) {
        this.usuarioAtual = usuarioAtual;
        this.relatorioService = relatorioService;
    }

    @GetMapping("/mes")
    public RelatorioDoMesResponse doMes(@RequestParam(required = false) YearMonth mes) {
        return relatorioService.doMes(usuarioAtual.id(), mes);
    }
}
