package com.flux.exportacao.interno;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.stream.Collectors;

import com.flux.exportacao.interno.CatalogoDaExportacao.Coluna;
import com.flux.exportacao.interno.CatalogoDaExportacao.Tabela;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectWriter;

/**
 * O arquivo JSON completo (decisões 20, 22 e 23), escrito aos poucos: cada tabela do catálogo é lida pelo cursor do
 * JdbcClient da exportação e cada linha vai para a saída assim que chega. Nada é montado na memória.
 */
final class ArquivoJson {

    static final String FORMATO = "flux-exportacao";

    static final int VERSAO = 1;

    private ArquivoJson() {
    }

    /**
     * Escreve o arquivo inteiro. O gerador só fecha no fim: o close() do Jackson fecharia as listas e o objeto abertos
     * (AUTO_CLOSE_CONTENT), e um arquivo cortado por uma falha no meio pareceria inteiro. A exceção sobe, e o Tomcat
     * derruba a conexão (TratadorGlobalDeErros): o navegador vê o download incompleto.
     */
    static void escrever(JdbcClient jdbc, ObjectWriter escritor, Long usuarioId, Instant geradoEm, OutputStream saida) {
        JsonGenerator json = escritor.createGenerator(saida, JsonEncoding.UTF8);
        json.writeStartObject();
        json.writeStringProperty("formato", FORMATO);
        json.writeNumberProperty("versao", VERSAO);
        json.writeStringProperty("geradoEm", geradoEm.toString());
        json.writeStringProperty("foraDoArquivo", CatalogoDaExportacao.FORA_DO_ARQUIVO);
        for (Tabela tabela : CatalogoDaExportacao.TABELAS) {
            json.writeName(tabela.json());
            if (tabela.sql().equals("usuarios")) {
                boolean[] achou = {false};
                jdbc.sql(selecao(tabela) + " WHERE id = :usuarioId").param("usuarioId", usuarioId)
                        .query((RowCallbackHandler) registro -> {
                            achou[0] = true;
                            escreverRegistro(json, tabela, registro);
                        });
                if (!achou[0]) {
                    json.writeNull();
                }
            } else {
                json.writeStartArray();
                jdbc.sql(selecao(tabela) + " WHERE usuario_id = :usuarioId ORDER BY id").param("usuarioId", usuarioId)
                        .query((RowCallbackHandler) registro -> escreverRegistro(json, tabela, registro));
                json.writeEndArray();
            }
        }
        json.writeEndObject();
        json.close();
    }

    /** "SELECT id, nome, … FROM contas": os nomes vêm do catálogo, nunca do pedido. */
    private static String selecao(Tabela tabela) {
        return tabela.colunas().stream().map(Coluna::sql)
                .collect(Collectors.joining(", ", "SELECT ", " FROM " + tabela.sql()));
    }

    private static void escreverRegistro(JsonGenerator json, Tabela tabela, ResultSet registro) throws SQLException {
        json.writeStartObject();
        int indice = 1;
        for (Coluna coluna : tabela.colunas()) {
            json.writeName(coluna.json());
            escreverValor(json, coluna, registro, indice);
            indice++;
        }
        json.writeEndObject();
    }

    private static void escreverValor(JsonGenerator json, Coluna coluna, ResultSet registro, int indice) throws SQLException {
        switch (coluna.tipo()) {
            case INTEIRO -> {
                long valor = registro.getLong(indice);
                if (registro.wasNull()) {
                    json.writeNull();
                } else {
                    json.writeNumber(valor);
                }
            }
            case TEXTO -> {
                String valor = registro.getString(indice);
                if (valor == null) {
                    json.writeNull();
                } else {
                    json.writeString(valor);
                }
            }
            case DINHEIRO -> {
                BigDecimal valor = registro.getBigDecimal(indice);
                if (valor == null) {
                    json.writeNull();
                } else {
                    json.writeNumber(valor);
                }
            }
            case DATA, MES -> {
                LocalDate valor = registro.getObject(indice, LocalDate.class);
                if (valor == null) {
                    json.writeNull();
                } else {
                    json.writeString(coluna.tipo() == CatalogoDaExportacao.Tipo.MES ? YearMonth.from(valor).toString()
                            : valor.toString());
                }
            }
            case INSTANTE -> {
                OffsetDateTime valor = registro.getObject(indice, OffsetDateTime.class);
                if (valor == null) {
                    json.writeNull();
                } else {
                    json.writeString(valor.toInstant().toString());
                }
            }
            case BOOLEANO -> {
                boolean valor = registro.getBoolean(indice);
                if (registro.wasNull()) {
                    json.writeNull();
                } else {
                    json.writeBoolean(valor);
                }
            }
        }
    }
}
