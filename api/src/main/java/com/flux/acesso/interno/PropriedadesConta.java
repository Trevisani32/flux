package com.flux.acesso.interno;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.conta")
public record PropriedadesConta(String urlBase, boolean cadastroPublico, PropriedadesControlador controlador,
                                @DefaultValue("true") boolean limitePorIp) {

    public PropriedadesConta {
        urlBase = urlBase == null ? "" : urlBase.strip();
        if (!urlValida(urlBase)) {
            throw new IllegalArgumentException(
                    "app.conta.url-base (APP_URL_BASE) precisa ser uma URL http ou https, sem barra no fim");
        }
        controlador = controlador == null ? new PropriedadesControlador("", "") : controlador;
        if (cadastroPublico && (controlador.nome().isEmpty() || controlador.email().isEmpty())) {
            throw new IllegalArgumentException(
                    "Com APP_CADASTRO_PUBLICO=true, defina APP_CONTROLADOR_NOME e APP_CONTROLADOR_EMAIL");
        }
    }

    private static boolean urlValida(String url) {
        if (url.isEmpty() || url.endsWith("/")) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public record PropriedadesControlador(String nome, String email) {

        public PropriedadesControlador {
            nome = nome == null ? "" : nome.strip();
            email = email == null ? "" : email.strip();
        }
    }
}
