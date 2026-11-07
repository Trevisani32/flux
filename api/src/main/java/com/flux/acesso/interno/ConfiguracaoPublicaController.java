package com.flux.acesso.interno;

import com.flux.acesso.interno.dto.ConfiguracaoPublicaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/publico")
public class ConfiguracaoPublicaController {

    private final PropriedadesConta propriedades;

    public ConfiguracaoPublicaController(PropriedadesConta propriedades) {
        this.propriedades = propriedades;
    }

    @GetMapping("/configuracao")
    public ConfiguracaoPublicaResponse configuracao() {
        return new ConfiguracaoPublicaResponse(propriedades.cadastroPublico(),
                new ConfiguracaoPublicaResponse.Controlador(propriedades.controlador().nome(),
                        propriedades.controlador().email()),
                Termos.VERSAO);
    }
}
