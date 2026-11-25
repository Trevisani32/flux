package com.flux.exportacao.interno;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que vai para o arquivo JSON, tabela a tabela e coluna a coluna, e o que fica de fora (Fatia 06b, ADR 0021). Vai tudo o
 * que o usuário guardou, com os valores como estão no banco (ADR 0002); saldos e totais são calculados e não vão. Ficam de
 * fora a senha, as sessões, os links de e-mail, os hashes e as chaves internas, e o campo foraDoArquivo diz isso no próprio
 * arquivo. O CatalogoDaExportacaoIT confere contra o banco que toda tabela e toda coluna estão aqui, num lado ou no outro:
 * a migration que criar uma tabela ou uma coluna sem passar por este arquivo quebra o build.
 */
final class CatalogoDaExportacao {

    /** O tipo da coluna no banco e como ela vai para o JSON. */
    enum Tipo {
        /** BIGINT ou INTEGER: número. */
        INTEIRO,
        /** VARCHAR: texto. */
        TEXTO,
        /** NUMERIC(19,2): número com sinal e 2 casas, como a API responde (-150.00). */
        DINHEIRO,
        /** DATE: "2026-11-24". */
        DATA,
        /** DATE do dia 1 que guarda um mês: "2026-11". */
        MES,
        /** TIMESTAMPTZ: o instante em UTC, como a API responde ("2026-11-24T17:05:12Z"). */
        INSTANTE,
        /** BOOLEAN. */
        BOOLEANO
    }

    record Coluna(String sql, String json, Tipo tipo) {
    }

    /**
     * Uma tabela do arquivo: {@code json} é o nome da lista (em {@code usuarios}, o do objeto); {@code fora}, as colunas
     * que não vão.
     */
    record Tabela(String sql, String json, List<Coluna> colunas, Set<String> fora) {
    }

    /** O campo foraDoArquivo do JSON. */
    static final String FORA_DO_ARQUIVO = "senha, sessões, links de e-mail, hashes de arquivo e de extrato, chaves de "
            + "importação e de comparação e eventos internos; os arquivos importados nunca foram guardados";

    /** As tabelas do arquivo, na ordem dele. A primeira é o objeto "usuario"; as outras são listas, por id. */
    static final List<Tabela> TABELAS = List.of(
            new Tabela("usuarios", "usuario", List.of(
                    texto("nome", "nome"),
                    texto("email", "email"),
                    instante("criado_em", "criadoEm"),
                    instante("email_confirmado_em", "emailConfirmadoEm"),
                    texto("termos_versao", "termosVersao"),
                    instante("termos_aceitos_em", "termosAceitosEm"),
                    instante("atualizado_em", "atualizadoEm")),
                    Set.of("id", "senha_hash")),
            new Tabela("contas", "contas", List.of(
                    inteiro("id", "id"),
                    texto("nome", "nome"),
                    texto("tipo", "tipo"),
                    dinheiro("saldo_inicial", "saldoInicial"),
                    data("data_saldo_inicial", "dataSaldoInicial"),
                    texto("cor", "cor"),
                    instante("arquivada_em", "arquivadaEm"),
                    instante("criada_em", "criadaEm"),
                    instante("atualizada_em", "atualizadaEm")),
                    Set.of("usuario_id", "nome_chave")),
            new Tabela("categorias", "categorias", List.of(
                    inteiro("id", "id"),
                    texto("natureza", "natureza"),
                    texto("nome", "nome"),
                    inteiro("categoria_mae_id", "categoriaMaeId"),
                    instante("arquivada_em", "arquivadaEm"),
                    booleano("arquivada_com_a_mae", "arquivadaComAMae"),
                    instante("criada_em", "criadaEm"),
                    instante("atualizada_em", "atualizadaEm")),
                    Set.of("usuario_id", "nome_chave")),
            new Tabela("cartoes", "cartoes", List.of(
                    inteiro("id", "id"),
                    texto("nome", "nome"),
                    dinheiro("limite", "limite"),
                    inteiro("dia_fechamento", "diaFechamento"),
                    inteiro("dia_vencimento", "diaVencimento"),
                    inteiro("conta_pagamento_id", "contaPagamentoId"),
                    texto("cor", "cor"),
                    instante("arquivado_em", "arquivadoEm"),
                    instante("criado_em", "criadoEm"),
                    instante("atualizado_em", "atualizadoEm")),
                    Set.of("usuario_id", "nome_chave")),
            new Tabela("faturas", "faturas", List.of(
                    inteiro("id", "id"),
                    inteiro("cartao_id", "cartaoId"),
                    mes("mes_referencia", "mesReferencia"),
                    data("data_fechamento", "fechamento"),
                    data("data_vencimento", "vencimento"),
                    booleano("fechamento_ajustado", "fechamentoAjustado"),
                    data("fechamento_normal", "fechamentoNormal"),
                    instante("paga_fora_em", "pagaForaEm"),
                    instante("criada_em", "criadaEm")),
                    Set.of("usuario_id")),
            new Tabela("compras", "compras", List.of(
                    inteiro("id", "id"),
                    inteiro("cartao_id", "cartaoId"),
                    texto("tipo", "tipo"),
                    data("data_compra", "dataCompra"),
                    dinheiro("valor_total", "valorTotal"),
                    inteiro("parcelas", "parcelas"),
                    inteiro("parcela_inicial", "parcelaInicial"),
                    inteiro("importacao_id", "importacaoId"),
                    instante("criada_em", "criadaEm"),
                    instante("atualizada_em", "atualizadaEm")),
                    Set.of("usuario_id")),
            new Tabela("lancamentos", "lancamentos", List.of(
                    inteiro("id", "id"),
                    data("data", "data"),
                    texto("descricao", "descricao"),
                    dinheiro("valor", "valor"),
                    texto("tipo", "tipo"),
                    inteiro("categoria_id", "categoriaId"),
                    inteiro("conta_id", "contaId"),
                    inteiro("cartao_id", "cartaoId"),
                    inteiro("fatura_id", "faturaId"),
                    inteiro("compra_id", "compraId"),
                    inteiro("numero_parcela", "numeroParcela"),
                    inteiro("transferencia_id", "transferenciaId"),
                    inteiro("fatura_paga_id", "faturaPagaId"),
                    texto("origem", "origem"),
                    inteiro("importacao_id", "importacaoId"),
                    texto("descricao_banco", "descricaoBanco"),
                    texto("observacao", "observacao"),
                    instante("ignorado_em", "ignoradoEm"),
                    instante("criado_em", "criadoEm"),
                    instante("atualizado_em", "atualizadoEm")),
                    Set.of("usuario_id", "descricao_chave", "chave_importacao")),
            new Tabela("transferencias", "transferencias", List.of(
                    inteiro("id", "id"),
                    instante("criada_em", "criadaEm")),
                    Set.of("usuario_id")),
            new Tabela("importacoes", "importacoes", List.of(
                    inteiro("id", "id"),
                    inteiro("conta_id", "contaId"),
                    inteiro("cartao_id", "cartaoId"),
                    inteiro("fatura_id", "faturaId"),
                    texto("formato", "formato"),
                    texto("nome_arquivo", "nomeArquivo"),
                    texto("extrato_final", "finalDoExtrato"),
                    data("data_inicio", "dataInicio"),
                    data("data_fim", "dataFim"),
                    inteiro("lidas", "lidas"),
                    inteiro("importadas", "importadas"),
                    inteiro("duplicadas", "duplicadas"),
                    inteiro("anteriores", "anteriores"),
                    inteiro("fora", "fora"),
                    inteiro("desmarcadas", "desmarcadas"),
                    instante("criada_em", "criadaEm"),
                    instante("desfeita_em", "desfeitaEm")),
                    Set.of("usuario_id", "hash_arquivo", "extrato_hash")),
            new Tabela("perfis_csv", "perfisCsv", List.of(
                    inteiro("id", "id"),
                    inteiro("conta_id", "contaId"),
                    inteiro("cartao_id", "cartaoId"),
                    texto("separador", "separador"),
                    inteiro("linhas_de_cabecalho", "linhasDeCabecalho"),
                    inteiro("coluna_data", "colunaData"),
                    texto("formato_data", "formatoData"),
                    inteiro("coluna_descricao", "colunaDescricao"),
                    inteiro("coluna_valor", "colunaValor"),
                    inteiro("coluna_saidas", "colunaSaidas"),
                    inteiro("coluna_identificador", "colunaIdentificador"),
                    texto("separador_decimal", "separadorDecimal"),
                    booleano("compras_positivas", "comprasPositivas"),
                    instante("atualizado_em", "atualizadoEm")),
                    Set.of("usuario_id")),
            new Tabela("regras_categorizacao", "regras", List.of(
                    inteiro("id", "id"),
                    inteiro("posicao", "posicao"),
                    texto("condicao", "condicao"),
                    texto("texto", "texto"),
                    texto("natureza", "natureza"),
                    inteiro("categoria_id", "categoriaId"),
                    inteiro("conta_id", "contaId"),
                    inteiro("cartao_id", "cartaoId"),
                    texto("descricao", "descricao"),
                    instante("criada_em", "criadaEm"),
                    instante("atualizada_em", "atualizadaEm")),
                    Set.of("usuario_id", "texto_chave")));

    /** As tabelas que não vão, com o motivo. */
    static final Map<String, String> TABELAS_FORA = Map.of(
            "tokens_renovacao", "as sessões",
            "tokens_email", "os links enviados por e-mail",
            "event_publication", "os eventos internos",
            "flyway_schema_history", "o controle das migrations");

    private CatalogoDaExportacao() {
    }

    private static Coluna inteiro(String sql, String json) {
        return new Coluna(sql, json, Tipo.INTEIRO);
    }

    private static Coluna texto(String sql, String json) {
        return new Coluna(sql, json, Tipo.TEXTO);
    }

    private static Coluna dinheiro(String sql, String json) {
        return new Coluna(sql, json, Tipo.DINHEIRO);
    }

    private static Coluna data(String sql, String json) {
        return new Coluna(sql, json, Tipo.DATA);
    }

    private static Coluna mes(String sql, String json) {
        return new Coluna(sql, json, Tipo.MES);
    }

    private static Coluna instante(String sql, String json) {
        return new Coluna(sql, json, Tipo.INSTANTE);
    }

    private static Coluna booleano(String sql, String json) {
        return new Coluna(sql, json, Tipo.BOOLEANO);
    }
}
