package com.flux.categorias.interno;

import java.util.List;

import com.flux.compartilhado.texto.TextoComparavel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriasPadraoTest {

    @Test
    void listaTemAs24CategoriasDoProduto() {
        List<CategoriasPadrao.Padrao> despesas = CategoriasPadrao.LISTA.stream()
                .filter(padrao -> padrao.natureza() == Natureza.DESPESA).toList();
        List<CategoriasPadrao.Padrao> receitas = CategoriasPadrao.LISTA.stream()
                .filter(padrao -> padrao.natureza() == Natureza.RECEITA).toList();

        assertThat(despesas).extracting(CategoriasPadrao.Padrao::nome).containsExactly("Moradia", "Alimentação",
                "Transporte", "Saúde", "Educação", "Lazer", "Assinaturas", "Compras", "Impostos e taxas", "Outras despesas");
        assertThat(receitas).extracting(CategoriasPadrao.Padrao::nome)
                .containsExactly("Salário", "Freelance", "Rendimentos", "Reembolsos", "Outras receitas");
        assertThat(despesas.get(0).subcategorias()).containsExactly("Aluguel", "Condomínio", "Contas da casa");
        assertThat(despesas.get(1).subcategorias()).containsExactly("Mercado", "Restaurante", "Delivery");
        assertThat(despesas.get(2).subcategorias()).containsExactly("Combustível", "App de transporte", "Transporte público");
        assertThat(despesas.subList(3, despesas.size())).allMatch(padrao -> padrao.subcategorias().isEmpty());
        assertThat(receitas).allMatch(padrao -> padrao.subcategorias().isEmpty());
        assertThat(CategoriasPadrao.LISTA.stream().mapToInt(padrao -> 1 + padrao.subcategorias().size()).sum()).isEqualTo(24);
    }

    @Test
    void nomesCabemNaColunaENaoSeRepetemEntreIrmas() {
        CategoriasPadrao.LISTA.forEach(padrao -> {
            assertThat(padrao.nome()).hasSizeLessThanOrEqualTo(60).isEqualTo(TextoComparavel.limparEspacos(padrao.nome()));
            assertThat(padrao.subcategorias().stream().map(TextoComparavel::chave).distinct().count())
                    .isEqualTo(padrao.subcategorias().size());
        });
        for (Natureza natureza : Natureza.values()) {
            List<String> chaves = CategoriasPadrao.LISTA.stream().filter(padrao -> padrao.natureza() == natureza)
                    .map(padrao -> TextoComparavel.chave(padrao.nome())).toList();
            assertThat(chaves).doesNotHaveDuplicates();
        }
    }
}
