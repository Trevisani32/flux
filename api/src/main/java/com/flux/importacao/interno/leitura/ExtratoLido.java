package com.flux.importacao.interno.leitura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.flux.compartilhado.texto.TextoComparavel;

/**
 * O que um {@link LeitorExtrato} tirou do arquivo. {@code bancoId} e {@code contaDoBanco} só no
 * OFX (BANKID e ACCTID da primeira conta); {@code contas} conta os extratos de conta corrente e
 * {@code cartao} diz se há extrato de cartão (decisão 10).
 */
public record ExtratoLido(Formato formato, List<Linha> linhas, String bancoId, String contaDoBanco, int contas, boolean cartao) {

    public enum Formato { OFX, CSV }

    /**
     * O maior valor de um lançamento (o @DecimalMax do lançamento manual e o NUMERIC(19,2) do
     * banco): acima dele, a linha é ilegível, e não um 500 na confirmação nem um importado que
     * não se salva no Editar.
     */
    public static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999999999.99");

    /** O valor lido cabe num lançamento: até 2 casas e até {@link #VALOR_MAXIMO}, com qualquer sinal. */
    public static boolean valorAceito(BigDecimal valor) {
        return valor.scale() <= 2 && valor.abs().compareTo(VALOR_MAXIMO) <= 0;
    }

    /**
     * Uma movimentação, na ordem do arquivo. {@code numero} conta a partir de 1 (no CSV, é o
     * número do registro, com o cabeçalho). Linha ilegível: {@code erro} preenchido e data ou
     * valor nulos. {@code descricao} já vem limpa e cortada em 255; {@code descricaoCompleta},
     * limpa e inteira (vai para a chave); {@code identificador} só quando o banco manda.
     */
    public record Linha(int numero, LocalDate data, BigDecimal valor, String descricao, String descricaoCompleta,
                        String identificador, String erro) {

        static final String SEM_DESCRICAO = "Sem descrição";

        /**
         * Descrição sem espaços sobrando (como os nomes); vazia, "Sem descrição"; acima de 255
         * caracteres, cortada (decisão 9). Identificador vazio ou acima de 255 é descartado.
         */
        static Linha legivel(int numero, LocalDate data, BigDecimal valor, String descricao, String identificador) {
            String limpa = descricao == null ? "" : TextoComparavel.limparEspacos(descricao);
            String completa = limpa.isEmpty() ? SEM_DESCRICAO : limpa;
            String curta = completa.length() > 255 ? completa.substring(0, 255).strip() : completa;
            String id = identificador == null || identificador.isBlank() || identificador.strip().length() > 255 ? null
                    : identificador.strip();
            return new Linha(numero, data, valor.setScale(2), curta, completa, id, null);
        }

        static Linha ilegivel(int numero, String erro) {
            return new Linha(numero, null, null, null, null, null, erro);
        }

        public boolean legivel() {
            return erro == null;
        }
    }
}
