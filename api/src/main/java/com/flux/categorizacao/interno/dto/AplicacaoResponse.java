package com.flux.categorizacao.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/regras/aplicacao (decisão 17): os lançamentos sem categoria que as regras pegam, agrupados
 * pela primeira regra que casa, na ordem da posição. Nada foi gravado.
 */
public record AplicacaoResponse(int total, List<Grupo> grupos) {

    /**
     * Uma regra e os lançamentos dela: {@code manuais} são os lançados à mão (só a categoria muda);
     * {@code renomeados}, os importados que ganham a descrição legível. Até 3 exemplos.
     */
    public record Grupo(Long regraId, int posicao, String condicao, String texto, Long categoriaId, String categoriaNome,
                        String categoriaMaeNome, String descricao, int quantidade, int manuais, int renomeados,
                        List<Exemplo> exemplos) {
    }

    /** {@code descricao}: a atual do lançamento. */
    public record Exemplo(LocalDate data, String descricao, String contaNome, BigDecimal valor) {
    }
}
