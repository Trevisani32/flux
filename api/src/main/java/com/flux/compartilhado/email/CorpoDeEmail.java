package com.flux.compartilhado.email;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.util.HtmlUtils;

/**
 * Monta o HTML (estilos em linha, sem imagens, fundo claro) e o texto simples de um
 * e-mail do Flux. Todo texto recebido é escapado no HTML.
 */
public final class CorpoDeEmail {

    private sealed interface Bloco permits Paragrafo, Botao, Link, Pequeno, Endereco {
    }

    private record Paragrafo(String texto) implements Bloco {
    }

    private record Botao(String rotulo, String url) implements Bloco {
    }

    private record Link(String rotulo, String url) implements Bloco {
    }

    private record Pequeno(String texto) implements Bloco {
    }

    private record Endereco(String url) implements Bloco {
    }

    private final String titulo;
    private final List<Bloco> blocos = new ArrayList<>();
    private String rodape;

    private CorpoDeEmail(String titulo) {
        this.titulo = titulo;
    }

    public static CorpoDeEmail comTitulo(String titulo) {
        return new CorpoDeEmail(titulo);
    }

    public CorpoDeEmail paragrafo(String texto) {
        blocos.add(new Paragrafo(texto));
        return this;
    }

    public CorpoDeEmail botao(String rotulo, String url) {
        blocos.add(new Botao(rotulo, url));
        return this;
    }

    public CorpoDeEmail link(String rotulo, String url) {
        blocos.add(new Link(rotulo, url));
        return this;
    }

    public CorpoDeEmail pequeno(String texto) {
        blocos.add(new Pequeno(texto));
        return this;
    }

    public CorpoDeEmail endereco(String url) {
        blocos.add(new Endereco(url));
        return this;
    }

    public CorpoDeEmail rodape(String texto) {
        this.rodape = texto;
        return this;
    }

    public MensagemDeEmail para(String destinatario, String assunto) {
        return new MensagemDeEmail(destinatario, assunto, html(assunto), texto());
    }

    private String html(String assunto) {
        StringBuilder html = new StringBuilder()
                .append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(e(assunto)).append("</title></head>")
                .append("<body style=\"margin:0;padding:24px 12px;background:#F4F4F5;")
                .append("font-family:Manrope,'Segoe UI',Arial,sans-serif;color:#18181B\">")
                .append("<div style=\"max-width:560px;margin:0 auto;background:#FFFFFF;")
                .append("border:1px solid #E4E4E7;border-radius:16px;overflow:hidden\">")
                .append("<div style=\"background:#09090B;padding:18px 28px;color:#D4AF37;")
                .append("font-size:20px;font-weight:800;letter-spacing:-0.4px\">Flux</div>")
                .append("<div style=\"padding:30px 28px 26px\">")
                .append("<h1 style=\"margin:0 0 16px;font-size:22px;line-height:30px;font-weight:800;")
                .append("color:#0B0B0C\">").append(e(titulo)).append("</h1>");
        for (Bloco bloco : blocos) {
            switch (bloco) {
                case Paragrafo p -> html
                        .append("<p style=\"margin:0 0 16px;font-size:15px;line-height:24px;color:#3F3F46\">")
                        .append(e(p.texto())).append("</p>");
                case Botao b -> html
                        .append("<p style=\"margin:0 0 16px\"><a href=\"").append(e(b.url()))
                        .append("\" style=\"display:inline-block;padding:13px 22px;border-radius:12px;")
                        .append("background:#D4AF37;color:#0B0B0C;font-weight:800;text-decoration:none\">")
                        .append(e(b.rotulo())).append("</a></p>");
                case Link l -> html
                        .append("<p style=\"margin:0 0 16px;font-size:15px;line-height:24px\"><a href=\"")
                        .append(e(l.url())).append("\" style=\"color:#846510;font-weight:700\">")
                        .append(e(l.rotulo())).append("</a></p>");
                case Pequeno p -> html
                        .append("<p style=\"margin:0 0 16px;font-size:13px;line-height:20px;color:#52525B\">")
                        .append(e(p.texto())).append("</p>");
                case Endereco a -> html
                        .append("<p style=\"margin:0 0 16px;font-size:12px;line-height:18px;color:#846510;")
                        .append("word-break:break-all\">").append(e(a.url())).append("</p>");
            }
        }
        html.append("</div>");
        if (rodape != null) {
            html.append("<div style=\"padding:16px 28px 22px;border-top:1px solid #F4F4F5;font-size:12px;")
                    .append("line-height:18px;color:#71717A\">").append(e(rodape)).append("</div>");
        }
        return html.append("</div></body></html>").toString();
    }

    private String texto() {
        StringBuilder texto = new StringBuilder("Flux\n\n").append(titulo).append("\n\n");
        for (Bloco bloco : blocos) {
            switch (bloco) {
                case Paragrafo p -> texto.append(p.texto()).append("\n\n");
                case Botao b -> texto.append(b.rotulo()).append(": ").append(b.url()).append("\n\n");
                case Link l -> texto.append(l.rotulo()).append(": ").append(l.url()).append("\n\n");
                case Pequeno p -> texto.append(p.texto()).append("\n\n");
                case Endereco a -> texto.append(a.url()).append("\n\n");
            }
        }
        if (rodape != null) {
            texto.append("--\n").append(rodape).append("\n");
        }
        return texto.toString();
    }

    private static String e(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }
}
