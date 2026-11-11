package com.flux.importacao.interno.leitura;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Como ler o CSV de uma conta (decisão 12). As colunas contam a partir de 1. Sem
 * {@code colunaSaidas}, a coluna do valor traz as saídas negativas; com ela, a do valor traz as
 * entradas e a outra, as saídas (com ou sem sinal). É também o corpo de GET e PUT
 * /api/importacoes/perfis/{contaId}.
 */
public record FormatoCsv(
        @NotNull(message = "Escolha o separador.") Separador separador,
        @NotNull(message = "Informe as linhas de cabeçalho.")
        @Min(value = 0, message = "Use de 0 a 20 linhas.") @Max(value = 20, message = "Use de 0 a 20 linhas.")
        Integer linhasDeCabecalho,
        @NotNull(message = "Escolha a coluna da data.")
        @Min(value = 1, message = COLUNA) @Max(value = 50, message = COLUNA) Integer colunaData,
        @NotNull(message = "Escolha o formato da data.") FormatoData formatoData,
        @NotNull(message = "Escolha a coluna da descrição.")
        @Min(value = 1, message = COLUNA) @Max(value = 50, message = COLUNA) Integer colunaDescricao,
        @NotNull(message = "Escolha a coluna do valor.")
        @Min(value = 1, message = COLUNA) @Max(value = 50, message = COLUNA) Integer colunaValor,
        @Min(value = 1, message = COLUNA) @Max(value = 50, message = COLUNA) Integer colunaSaidas,
        @Min(value = 1, message = COLUNA) @Max(value = 50, message = COLUNA) Integer colunaIdentificador,
        @NotNull(message = "Escolha o separador decimal.") SeparadorDecimal separadorDecimal) {

    static final String COLUNA = "Use uma coluna de 1 a 50.";

    /** O CSV da conta do Nubank: Data,Valor,Identificador,Descrição (decisão 6). */
    public static final FormatoCsv NUBANK_CONTA = new FormatoCsv(Separador.VIRGULA, 1, 1, FormatoData.DD_MM_AAAA, 4, 2, null, 3,
            SeparadorDecimal.PONTO);

    public enum Separador {
        VIRGULA(','), PONTO_E_VIRGULA(';'), TAB('\t');

        private final char caractere;

        Separador(char caractere) {
            this.caractere = caractere;
        }

        public char caractere() {
            return caractere;
        }
    }

    public enum SeparadorDecimal { VIRGULA, PONTO }

    public enum FormatoData {
        DD_MM_AAAA("dd/MM/uuuu"), DD_MM_AA("dd/MM/uu"), AAAA_MM_DD("uuuu-MM-dd");

        private final DateTimeFormatter formatador;

        FormatoData(String padrao) {
            this.formatador = DateTimeFormatter.ofPattern(padrao).withResolverStyle(ResolverStyle.STRICT);
        }

        public DateTimeFormatter formatador() {
            return formatador;
        }
    }
}
