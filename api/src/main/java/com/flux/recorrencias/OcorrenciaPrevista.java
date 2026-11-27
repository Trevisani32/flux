package com.flux.recorrencias;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma ocorrência prevista ou atrasada (ADR 0022). {@code periodo} é a chave (o primeiro dia do mês, da semana ou do ano);
 * {@code data} é a prevista (no cartão, a da compra); {@code valor} tem o sinal do tipo e {@code valorPrevisto} é positivo,
 * os dois com escala 2 (ADR 0002); {@code tipo} é "RECEITA" ou "DESPESA"; conta ou cartão, nunca os dois; {@code cartaoCor} é
 * o nome da cor da paleta ou nulo; {@code visivelAte} só na atrasada (a data + 60 dias).
 */
public record OcorrenciaPrevista(Long recorrenciaId, LocalDate periodo, LocalDate data, String descricao, String tipo,
                                 BigDecimal valor, BigDecimal valorPrevisto, boolean valorVaria, Long contaId, String contaNome,
                                 Long cartaoId, String cartaoNome, String cartaoCor, Long categoriaId, String categoriaNome,
                                 String categoriaMaeNome, boolean atrasada, LocalDate visivelAte) {
}
