package com.flux.importacao.interno.leitura;

import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Como ler o CSV de uma conta (decisão 12) ou, desde a Fatia 05c, de um cartão (decisão 39). As colunas contam a partir
 * de 1. Sem {@code colunaSaidas}, a coluna do valor traz as saídas negativas; com ela, a do valor traz as
 * entradas e a outra, as saídas (com ou sem sinal). {@code comprasPositivas} (só no cartão; nulo na conta): na coluna
 * única, as compras vêm positivas e os créditos negativos, e a leitura troca o sinal. É também o corpo de GET e PUT
 * /api/importacoes/perfis/{contaId} e /api/importacoes/perfis/cartao/{cartaoId}.
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
        @NotNull(message = "Escolha o separador decimal.") SeparadorDecimal separadorDecimal,
        Boolean comprasPositivas) {

    static final String COLUNA = "Use uma coluna de 1 a 50.";

    /** O CSV da conta do Nubank: Data,Valor,Identificador,Descrição (decisão 6). */
    public static final FormatoCsv NUBANK_CONTA = new FormatoCsv(Separador.VIRGULA, 1, 1, FormatoData.DD_MM_AAAA, 4, 2, null, 3,
            SeparadorDecimal.PONTO);

    /**
     * Fatia 05c (decisão 39): o CSV da fatura do cartão Nubank, date,title,amount (data AAAA-MM-DD, ponto decimal, compra
     * positiva, pagamento e crédito negativos).
     */
    public static final FormatoCsv NUBANK_FATURA = new FormatoCsv(Separador.VIRGULA, 1, 1, FormatoData.AAAA_MM_DD, 2, 3, null, null,
            SeparadorDecimal.PONTO, true);

    /** O formato de conta, sem {@code comprasPositivas}. */
    public FormatoCsv(Separador separador, Integer linhasDeCabecalho, Integer colunaData, FormatoData formatoData, Integer colunaDescricao,
                      Integer colunaValor, Integer colunaSaidas, Integer colunaIdentificador, SeparadorDecimal separadorDecimal) {
        this(separador, linhasDeCabecalho, colunaData, formatoData, colunaDescricao, colunaValor, colunaSaidas, colunaIdentificador,
                separadorDecimal, null);
    }

    /** Na coluna única, as compras vêm positivas: a leitura troca o sinal (só no cartão). */
    public boolean trocaOSinal() {
        return Boolean.TRUE.equals(comprasPositivas) && colunaSaidas == null;
    }

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
