package com.flux.lancamentos.interno;

import java.time.YearMonth;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.texto.TextoComparavel;

/**
 * Filtros da lista do mês, lidos da URL (decisão 13). {@code mes} nulo é o mês de hoje;
 * {@code contaId} ou {@code cartaoId} (Fatia 05a, decisão 23: nunca os dois); {@code categoriaId} é a
 * principal (com as subcategorias) ou a subcategoria; {@code semCategoria} é o filtro "Sem categoria".
 */
public record FiltroDaLista(
        YearMonth mes,
        Long contaId,
        Long cartaoId,
        Long categoriaId,
        boolean semCategoria,
        TipoLancamento tipo,
        String busca) {

    static final int TAMANHO_MAXIMO_DA_BUSCA = 100;

    /**
     * Lê os parâmetros: {@code categoria} é "sem" ou um id; {@code tipo} é "receita",
     * "despesa" ou "transferencia"; a busca vazia é ignorada.
     */
    public static FiltroDaLista de(YearMonth mes, Long conta, Long cartao, String categoria, String tipo, String busca) {
        if (conta != null && cartao != null) {
            throw new CampoInvalidoException("cartao", "Filtre por uma conta ou por um cartão, não pelos dois.");
        }
        Long categoriaId = null;
        boolean semCategoria = false;
        if (categoria != null && !categoria.isBlank()) {
            if (categoria.equals("sem")) {
                semCategoria = true;
            } else {
                try {
                    categoriaId = Long.valueOf(categoria);
                } catch (NumberFormatException ex) {
                    throw new CampoInvalidoException("categoria", "Filtro de categoria inválido.");
                }
            }
        }
        TipoLancamento tipoLido = null;
        if (tipo != null && !tipo.isBlank()) {
            tipoLido = switch (tipo) {
                case "receita" -> TipoLancamento.RECEITA;
                case "despesa" -> TipoLancamento.DESPESA;
                case "transferencia" -> TipoLancamento.TRANSFERENCIA;
                default -> throw new CampoInvalidoException("tipo", "Filtro de tipo inválido.");
            };
        }
        String buscaLimpa = busca == null ? "" : TextoComparavel.limparEspacos(busca);
        if (buscaLimpa.length() > TAMANHO_MAXIMO_DA_BUSCA) {
            throw new CampoInvalidoException("busca", "Use no máximo " + TAMANHO_MAXIMO_DA_BUSCA + " caracteres.");
        }
        return new FiltroDaLista(mes, conta, cartao, categoriaId, semCategoria, tipoLido, buscaLimpa.isEmpty() ? null : buscaLimpa);
    }
}
