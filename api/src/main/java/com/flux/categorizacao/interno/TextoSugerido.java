package com.flux.categorizacao.interno;

import java.util.regex.Pattern;

import com.flux.compartilhado.texto.TextoComparavel;

/**
 * O texto que vem preenchido em "Criar regra" (decisão 21): a descrição do extrato até o primeiro
 * trecho com número ou com "•" (o CPF mascarado), sem separador sobrando no fim. Com menos de 3 letras,
 * vai a descrição inteira. "PAG*UBERTRIP 8812" vira "PAG*UBERTRIP"; "PIX TRANSF JOAO F05/11", "PIX
 * TRANSF JOAO". O usuário edita antes de salvar.
 */
final class TextoSugerido {

    private static final Pattern NUMERO_OU_MASCARA = Pattern.compile(".*[0-9•].*");
    private static final Pattern SEPARADOR_NO_FIM = Pattern.compile("[\\s\\-*/.:,]+$");

    private TextoSugerido() {
    }

    static String de(String descricaoDoExtrato) {
        String limpa = TextoComparavel.limparEspacos(descricaoDoExtrato);
        StringBuilder texto = new StringBuilder();
        for (String pedaco : limpa.split(" ")) {
            if (NUMERO_OU_MASCARA.matcher(pedaco).matches()) {
                break;
            }
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            texto.append(pedaco);
        }
        String semSeparador = SEPARADOR_NO_FIM.matcher(texto).replaceAll("");
        long letras = semSeparador.codePoints().filter(Character::isLetter).count();
        return letras < 3 ? limpa : semSeparador;
    }
}
