package com.flux.importacao.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c (decisão 51): a exclusão do usuário com importações de fatura, perfis de cartão e regras com cartão não
 * esbarra em nenhuma FK e não deixa nada; a do cartão com compras importadas é recusada pelo uso, e a do cartão só com a
 * importação desfeita leva junto o registro, o perfil e as regras dele.
 */
class ExclusaoComImportacaoDeFaturaIT extends FaturaBase {

    private static final List<String> TABELAS = List.of("importacoes", "perfis_csv", "regras_categorizacao", "compras", "lancamentos",
            "faturas", "cartoes", "contas");

    @Test
    void excluirOUsuarioApagaAsImportacoesDeFaturaEOOutroFicaIgual() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        for (UsuarioDeTeste usuario : List.of(a, b)) {
            long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
            perfilDoNubank(usuario, horizonte);
            importarNoCartao(usuario, horizonte, "fatura.csv", csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"),
                    linha(dia(m().minusMonths(4), 20), "Curso Online Ficticio - Parcela 3/10", "120.00")), 2, 3);
            api.idCriado(api.post(usuario, "/api/regras", "{\"condicao\":\"CONTEM\",\"texto\":\"curso\",\"contaId\":null,\"cartaoId\":"
                    + horizonte + ",\"categoriaId\":" + idDaCategoria(usuario, "Educação") + ",\"descricao\":null}"));
        }
        List<Integer> deB = TABELAS.stream().map(tabela -> contar(tabela, b)).toList();

        jdbc.update("delete from usuarios where id = ?", a.id());

        assertThat(TABELAS.stream().map(tabela -> contar(tabela, a)).toList()).containsOnly(0);
        assertThat(TABELAS.stream().map(tabela -> contar(tabela, b)).toList()).isEqualTo(deB);
        assertThat(deB.get(0)).isEqualTo(1);
    }

    @Test
    void oCartaoComCompraImportadaNaoSeExcluiESoComAImportacaoDesfeitaSaiComTudo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        long importacao = importarNoCartao(usuario, horizonte, "fatura.csv",
                csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50")), 2);
        api.idCriado(api.post(usuario, "/api/regras", "{\"condicao\":\"CONTEM\",\"texto\":\"padaria\",\"contaId\":null,\"cartaoId\":"
                + horizonte + ",\"categoriaId\":" + idDaCategoria(usuario, "Mercado") + ",\"descricao\":null}"));

        MvcTestResult emUso = api.delete(usuario, "/api/cartoes/{id}", horizonte);
        assertThat(emUso).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Este cartão não pode ser excluído porque tem 1 compra. Arquive o cartão para tirá-lo dos seletores.");

        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatusOk();
        assertThat(api.delete(usuario, "/api/cartoes/{id}", horizonte)).hasStatus(204);

        assertThat(contar("importacoes", usuario)).isZero();
        assertThat(contar("perfis_csv", usuario)).isZero();
        assertThat(contar("regras_categorizacao", usuario)).isZero();
        assertThat(contar("faturas", usuario)).isZero();
    }
}
