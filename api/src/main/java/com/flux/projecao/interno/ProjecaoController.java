package com.flux.projecao.interno;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.projecao.interno.dto.AvisoDaProjecaoResponse;
import com.flux.projecao.interno.dto.ProjecaoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projecao")
public class ProjecaoController {

    private final UsuarioAtual usuarioAtual;
    private final ProjecaoService projecao;

    public ProjecaoController(UsuarioAtual usuarioAtual, ProjecaoService projecao) {
        this.usuarioAtual = usuarioAtual;
        this.projecao = projecao;
    }

    @GetMapping
    public ProjecaoResponse projetar(@RequestParam(required = false) String ate, @RequestParam(required = false) String contas) {
        return projecao.projetar(usuarioAtual.id(), ate, contas);
    }

    @GetMapping("/aviso")
    public AvisoDaProjecaoResponse aviso() {
        return projecao.aviso(usuarioAtual.id());
    }
}
