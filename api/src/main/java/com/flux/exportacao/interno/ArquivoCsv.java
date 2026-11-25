package com.flux.exportacao.interno;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * O CSV dos lançamentos (decisão 24), para abrir direto no Excel em português: UTF-8 com BOM, ";" entre os campos, vírgula
 * decimal, datas DD/MM/AAAA e CRLF no fim de cada linha. Uma linha por lançamento não ignorado (a transferência tem uma
 * em cada conta), do mais antigo ao mais novo. Escrito aos poucos, como o JSON.
 */
final class ArquivoCsv {

    static final String CABECALHO = "Data;Descrição;Valor;Tipo;Categoria;Subcategoria;Conta;Cartão;Parcela;Data da compra;"
            + "Observação;Origem;Descrição do extrato";

    static final String FIM_DE_LINHA = "\r\n";

    /** O começo de texto que a planilha leria como fórmula: ganha um apóstrofo na frente. */
    private static final String INICIO_DE_FORMULA = "=+-@\t\r";

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    // A subcategoria mostra a principal em "Categoria". O cartão do pagamento de fatura é o da fatura paga. Os joins levam
    // o usuario_id junto (ADR 0007), embora as FKs já garantam o mesmo usuário.
    private static final String LANCAMENTOS = """
            SELECT l.data, l.descricao, l.valor, l.tipo,
                   coalesce(mae.nome, cat.nome) AS categoria,
                   CASE WHEN mae.id IS NULL THEN NULL ELSE cat.nome END AS subcategoria,
                   co.nome AS conta,
                   coalesce(ca.nome, cp.nome) AS cartao,
                   l.numero_parcela, cm.parcelas, cm.data_compra, l.observacao, l.origem, l.descricao_banco
            FROM lancamentos l
            LEFT JOIN categorias cat ON cat.id = l.categoria_id AND cat.usuario_id = l.usuario_id
            LEFT JOIN categorias mae ON mae.id = cat.categoria_mae_id AND mae.usuario_id = l.usuario_id
            LEFT JOIN contas co ON co.id = l.conta_id AND co.usuario_id = l.usuario_id
            LEFT JOIN cartoes ca ON ca.id = l.cartao_id AND ca.usuario_id = l.usuario_id
            LEFT JOIN faturas fp ON fp.id = l.fatura_paga_id AND fp.usuario_id = l.usuario_id
            LEFT JOIN cartoes cp ON cp.id = fp.cartao_id AND cp.usuario_id = l.usuario_id
            LEFT JOIN compras cm ON cm.id = l.compra_id AND cm.usuario_id = l.usuario_id
            WHERE l.usuario_id = :usuarioId AND l.ignorado_em IS NULL
            ORDER BY l.data, l.id
            """;

    private ArquivoCsv() {
    }

    /** Sem close(): quem fecha a resposta é o contêiner. Uma falha no meio sobe, como no JSON. */
    static void escrever(JdbcClient jdbc, Long usuarioId, OutputStream saida) throws IOException {
        Writer csv = new BufferedWriter(new OutputStreamWriter(saida, StandardCharsets.UTF_8), 16 * 1024);
        csv.write('\uFEFF');
        csv.write(CABECALHO);
        csv.write(FIM_DE_LINHA);
        jdbc.sql(LANCAMENTOS).param("usuarioId", usuarioId).query((RowCallbackHandler) registro -> {
            try {
                csv.write(paraLinha(registro));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
        csv.flush();
    }

    private static String paraLinha(ResultSet registro) throws SQLException {
        return String.join(";",
                dia(registro.getObject("data", LocalDate.class)),
                texto(registro.getString("descricao")),
                valor(registro.getBigDecimal("valor")),
                tipo(registro.getString("tipo")),
                texto(registro.getString("categoria")),
                texto(registro.getString("subcategoria")),
                texto(registro.getString("conta")),
                texto(registro.getString("cartao")),
                parcela(registro.getObject("numero_parcela", Integer.class), registro.getObject("parcelas", Integer.class)),
                dia(registro.getObject("data_compra", LocalDate.class)),
                texto(registro.getString("observacao")),
                origem(registro.getString("origem")),
                texto(registro.getString("descricao_banco"))) + FIM_DE_LINHA;
    }

    /** Texto livre: apóstrofo antes de fórmula e aspas só quando há separador, aspas ou quebra de linha. */
    static String texto(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String seguro = INICIO_DE_FORMULA.indexOf(valor.charAt(0)) >= 0 ? "'" + valor : valor;
        boolean precisaDeAspas = seguro.indexOf(';') >= 0 || seguro.indexOf('"') >= 0 || seguro.indexOf('\n') >= 0
                || seguro.indexOf('\r') >= 0;
        return precisaDeAspas ? "\"" + seguro.replace("\"", "\"\"") + "\"" : seguro;
    }

    /** -1850.00 vira "-1850,00": vírgula decimal, sem milhar, com o sinal do banco (ADR 0002). */
    static String valor(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }

    static String dia(LocalDate dia) {
        return dia == null ? "" : DIA.format(dia);
    }

    static String tipo(String tipo) {
        return switch (tipo) {
            case "RECEITA" -> "Receita";
            case "DESPESA" -> "Despesa";
            case "TRANSFERENCIA" -> "Transferência";
            case "PAGAMENTO_FATURA" -> "Pagamento de fatura";
            default -> texto(tipo);
        };
    }

    static String origem(String origem) {
        return switch (origem) {
            case "MANUAL" -> "Manual";
            case "IMPORTADO" -> "Importado";
            default -> texto(origem);
        };
    }

    /** "1 de 3" só na compra parcelada: "1/3" o Excel em português lê como 1º de março. */
    static String parcela(Integer numero, Integer parcelas) {
        return numero == null || parcelas == null || parcelas <= 1 ? "" : numero + " de " + parcelas;
    }
}
