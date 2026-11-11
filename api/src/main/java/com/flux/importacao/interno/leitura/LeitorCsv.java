package com.flux.importacao.interno.leitura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CSV no padrão RFC 4180 (aspas duplas, separador e quebra de linha dentro de aspas, "" como
 * aspa), lido com o perfil da conta (decisão 12). Linhas em branco são puladas. Uma linha que não
 * dá para ler vira erro dela, sem derrubar o arquivo.
 */
public final class LeitorCsv implements LeitorExtrato {

    /** Ponto decimal: "1234.56", "1,234.56", "-6"; nunca "6,00". */
    private static final Pattern COM_PONTO = Pattern.compile("-?(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d{1,2})?");
    /** Vírgula decimal: "1234,56", "1.234,56", "-6"; nunca "6.00". */
    private static final Pattern COM_VIRGULA = Pattern.compile("-?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d{1,2})?");

    private final FormatoCsv formato;

    public LeitorCsv(FormatoCsv formato) {
        this.formato = formato;
    }

    @Override
    public ExtratoLido ler(String texto) {
        List<List<String>> registros = registros(texto, formato.separador().caractere());
        List<ExtratoLido.Linha> linhas = new ArrayList<>();
        for (int i = formato.linhasDeCabecalho(); i < registros.size(); i++) {
            linhas.add(linha(i + 1, registros.get(i)));
        }
        return new ExtratoLido(ExtratoLido.Formato.CSV, linhas, null, null, 1, false);
    }

    private ExtratoLido.Linha linha(int numero, List<String> campos) {
        LocalDate data;
        try {
            data = LocalDate.parse(campo(campos, formato.colunaData()).strip(), formato.formatoData().formatador());
        } catch (DateTimeParseException ex) {
            return ExtratoLido.Linha.ilegivel(numero, "data ilegível");
        }
        BigDecimal valor = valor(campos);
        if (valor == null || !ExtratoLido.valorAceito(valor)) {
            return ExtratoLido.Linha.ilegivel(numero, "valor ilegível");
        }
        String identificador = formato.colunaIdentificador() == null ? null : campo(campos, formato.colunaIdentificador());
        return ExtratoLido.Linha.legivel(numero, data, valor, campo(campos, formato.colunaDescricao()), identificador);
    }

    /** Uma coluna com as saídas negativas; ou entradas numa e saídas (com ou sem sinal) na outra. */
    private BigDecimal valor(List<String> campos) {
        String principal = campo(campos, formato.colunaValor());
        if (formato.colunaSaidas() == null) {
            return numero(principal);
        }
        String saida = campo(campos, formato.colunaSaidas());
        BigDecimal entrada = principal.isBlank() ? BigDecimal.ZERO : numero(principal);
        BigDecimal saiu = saida.isBlank() ? BigDecimal.ZERO : numero(saida);
        if (entrada == null || saiu == null || (principal.isBlank() && saida.isBlank())) {
            return null;
        }
        return entrada.abs().subtract(saiu.abs());
    }

    /**
     * "1.234,56" com vírgula decimal, "1,234.56" com ponto; "R$" e espaços saem. O número precisa
     * seguir o separador do perfil: "-6.00" num perfil de vírgula decimal (ou "-6,00" num de ponto) é
     * nulo, e a linha fica ilegível, em vez de virar -600,00. Mais de 2 casas: nulo.
     */
    private BigDecimal numero(String texto) {
        String limpo = texto.replace("R$", "").replace(" ", "").replace("\u00a0", "").replace("+", "");
        boolean virgula = formato.separadorDecimal() == FormatoCsv.SeparadorDecimal.VIRGULA;
        if (!(virgula ? COM_VIRGULA : COM_PONTO).matcher(limpo).matches()) {
            return null;
        }
        return new BigDecimal(virgula ? limpo.replace(".", "").replace(',', '.') : limpo.replace(",", ""));
    }

    private static String campo(List<String> campos, int coluna) {
        return coluna <= campos.size() ? campos.get(coluna - 1) : "";
    }

    /** Os registros do arquivo, já separados em campos. */
    public static List<List<String>> registros(String texto, char separador) {
        List<List<String>> registros = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreAspas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreAspas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreAspas = true;
            } else if (c == separador) {
                atual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                fecharRegistro(registros, atual, campo);
                atual = new ArrayList<>();
            } else {
                campo.append(c);
            }
        }
        fecharRegistro(registros, atual, campo);
        return registros;
    }

    private static void fecharRegistro(List<List<String>> registros, List<String> atual, StringBuilder campo) {
        atual.add(campo.toString());
        campo.setLength(0);
        if (atual.stream().anyMatch(valor -> !valor.isBlank())) {
            registros.add(atual);
        }
    }
}
