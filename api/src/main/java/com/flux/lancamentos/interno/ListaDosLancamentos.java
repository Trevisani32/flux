package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.ListaDoMesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A lista do mês, os últimos lançamentos e o item de cada resposta. O mês tem no máximo
 * 1.000 lançamentos (decisão 34): os filtros, os totais e o saldo do dia são feitos em
 * memória, sobre os lançamentos do mês. Os importados ignorados ficam de fora (Fatia 04a). Desde a
 * Fatia 05a, a lista tem as parcelas e os estornos do cartão, na data do vencimento da fatura (decisão 22),
 * e o filtro de um cartão, que mostra a fatura do mês (decisão 23).
 */
@Service
public class ListaDosLancamentos {

    private static final int ULTIMOS = 5;

    private final LancamentoRepository repositorio;
    private final ContasConsulta contas;
    private final CartoesConsulta cartoes;
    private final CategoriasConsulta categorias;
    private final CompraRepository compras;
    private final FaturaRepository faturas;
    private final Clock clock;

    public ListaDosLancamentos(LancamentoRepository repositorio, ContasConsulta contas, CartoesConsulta cartoes,
                               CategoriasConsulta categorias, CompraRepository compras, FaturaRepository faturas, Clock clock) {
        this.repositorio = repositorio;
        this.contas = contas;
        this.cartoes = cartoes;
        this.categorias = categorias;
        this.compras = compras;
        this.faturas = faturas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListaDoMesResponse listar(Long usuarioId, FiltroDaLista filtro) {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth mes = filtro.mes() == null ? YearMonth.from(hoje) : filtro.mes();
        List<Lancamento> doMes = repositorio.findAllByUsuarioIdAndDataBetweenAndIgnoradoEmIsNull(usuarioId, mes.atDay(1),
                mes.atEndOfMonth());
        List<CategoriaResumo> todasAsCategorias = categorias.listar(usuarioId);
        Montagem montagem = new Montagem(contas.listar(usuarioId), cartoes.listar(usuarioId), todasAsCategorias,
                comprasDe(usuarioId, doMes), hoje);
        Set<Long> categoriasDoFiltro = filtro.categoriaId() == null ? Set.of()
                : idsComSubcategorias(todasAsCategorias, filtro.categoriaId());
        String chaveDaBusca = filtro.busca() == null ? null : TextoComparavel.chave(filtro.busca());
        Map<Long, List<Lancamento>> lados = ladosDasTransferencias(usuarioId, doMes);

        List<Linha> linhas = new ArrayList<>();
        for (Lancamento lancamento : doMes) {
            if (!lancamento.transferencia()) {
                if (passa(lancamento, filtro, categoriasDoFiltro, chaveDaBusca)) {
                    linhas.add(new Linha(montagem.doLancamento(lancamento), lancamento.getId()));
                }
                continue;
            }
            if (filtro.cartaoId() != null) {
                continue;
            }
            Par par = Par.de(lados.get(lancamento.getTransferenciaId()));
            // Cada transferência entra uma vez, pelo lado de referência: o da conta filtrada ou a saída.
            Lancamento referencia = filtro.contaId() == null ? par.saida()
                    : lancamento.getContaId().equals(filtro.contaId()) ? lancamento : null;
            if (referencia == null || !referencia.getId().equals(lancamento.getId())
                    || !passaTransferencia(referencia, filtro, chaveDaBusca)) {
                continue;
            }
            String direcao = filtro.contaId() == null ? null
                    : referencia.getId().equals(par.saida().getId()) ? "SAIDA" : "ENTRADA";
            linhas.add(new Linha(montagem.daTransferencia(par.saida(), par.entrada(), referencia, direcao),
                    Math.max(par.saida().getId(), par.entrada().getId())));
        }
        // Decisão 5: data mais recente primeiro; no mesmo dia, o último cadastrado em cima.
        linhas.sort(Comparator.comparing((Linha linha) -> linha.item().data()).reversed()
                .thenComparing(Comparator.comparingLong(Linha::ordem).reversed()));

        TreeMap<LocalDate, BigDecimal> saldos = saldosNoFimDoDia(usuarioId, filtro.contaId(), montagem, mes, doMes);
        Map<LocalDate, List<ItemDaLista>> porDia = new LinkedHashMap<>();
        linhas.forEach(linha -> porDia.computeIfAbsent(linha.item().data(), data -> new ArrayList<>()).add(linha.item()));
        List<ListaDoMesResponse.Dia> dias = porDia.entrySet().stream()
                .map(dia -> new ListaDoMesResponse.Dia(dia.getKey(), saldos.isEmpty() ? null : saldoAte(saldos, dia.getKey()),
                        List.copyOf(dia.getValue())))
                .toList();
        List<ItemDaLista> itens = linhas.stream().map(Linha::item).toList();
        return new ListaDoMesResponse(mes.toString(), hoje, totais(itens),
                (int) itens.stream().filter(item -> item.tipo() != TipoLancamento.TRANSFERENCIA).count(),
                (int) itens.stream().filter(item -> item.tipo() == TipoLancamento.TRANSFERENCIA).count(), dias,
                faturaDoFiltro(usuarioId, filtro.cartaoId(), montagem, mes, doMes, hoje));
    }

    /** Os 5 mais recentes de conta até hoje, sem os futuros e sem os de fatura (decisão 26 da 05a); a transferência é um item só. */
    @Transactional(readOnly = true)
    public List<ItemDaLista> ultimos(Long usuarioId) {
        LocalDate hoje = LocalDate.now(clock);
        List<Lancamento> recentes =
                repositorio.findTop10ByUsuarioIdAndContaIdIsNotNullAndDataLessThanEqualAndIgnoradoEmIsNullOrderByDataDescIdDesc(usuarioId, hoje);
        Montagem montagem = montagem(usuarioId, List.of());
        Map<Long, List<Lancamento>> lados = ladosDasTransferencias(usuarioId, recentes);
        List<ItemDaLista> itens = new ArrayList<>();
        Set<Long> transferenciasVistas = new HashSet<>();
        for (Lancamento lancamento : recentes) {
            if (itens.size() == ULTIMOS) {
                break;
            }
            if (!lancamento.transferencia()) {
                itens.add(montagem.doLancamento(lancamento));
            } else if (transferenciasVistas.add(lancamento.getTransferenciaId())) {
                Par par = Par.de(lados.get(lancamento.getTransferenciaId()));
                itens.add(montagem.daTransferencia(par.saida(), par.entrada(), par.saida(), null));
            }
        }
        return itens;
    }

    /** Receita ou despesa (de conta ou de fatura), como resposta de buscar, criar e editar. */
    @Transactional(readOnly = true)
    public ItemDaLista itemDoLancamento(Long usuarioId, Lancamento lancamento) {
        return montagem(usuarioId, List.of(lancamento)).doLancamento(lancamento);
    }

    /** A transferência inteira, como resposta de buscar, criar e editar. */
    @Transactional(readOnly = true)
    public ItemDaLista itemDaTransferencia(Long usuarioId, List<Lancamento> lados) {
        Par par = Par.de(lados);
        return montagem(usuarioId, List.of()).daTransferencia(par.saida(), par.entrada(), par.saida(), null);
    }

    private Montagem montagem(Long usuarioId, List<Lancamento> lancamentos) {
        return new Montagem(contas.listar(usuarioId), cartoes.listar(usuarioId), categorias.listar(usuarioId),
                comprasDe(usuarioId, lancamentos), LocalDate.now(clock));
    }

    /** As compras dos lançamentos de fatura (Fatia 05a), por id. */
    private Map<Long, Compra> comprasDe(Long usuarioId, List<Lancamento> lancamentos) {
        Set<Long> ids = lancamentos.stream().filter(Lancamento::deFatura).map(Lancamento::getCompraId).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : compras.findAllByUsuarioIdAndIdIn(usuarioId, ids).stream()
                .collect(Collectors.toMap(Compra::getId, compra -> compra));
    }

    /**
     * Decisão 23 da 05a: com o filtro de um cartão, a fatura do mês, com as datas, a situação e o total da fatura
     * inteira (os lançamentos do cartão no mês, sem os outros filtros). Cartão de outro usuário: sem faixa.
     */
    private ListaDoMesResponse.FaturaDoFiltro faturaDoFiltro(Long usuarioId, Long cartaoId, Montagem montagem, YearMonth mes,
                                                             List<Lancamento> doMes, LocalDate hoje) {
        CartaoResumo cartao = cartaoId == null ? null : montagem.cartao(cartaoId);
        if (cartao == null) {
            return null;
        }
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        CalendarioDoCartao.Datas datas = doCartao.datas(mes);
        BigDecimal soma = somar(doMes.stream().filter(lancamento -> cartaoId.equals(lancamento.getCartaoId())).map(Lancamento::getValor));
        return new ListaDoMesResponse.FaturaDoFiltro(cartao.id(), cartao.nome(), cartao.cor(), mes.toString(), datas.fechamento(),
                datas.vencimento(), doCartao.situacao(mes, hoje).name(), soma.negate().setScale(2, RoundingMode.UNNECESSARY));
    }

    private Map<Long, List<Lancamento>> ladosDasTransferencias(Long usuarioId, List<Lancamento> lancamentos) {
        Set<Long> ids = lancamentos.stream().filter(Lancamento::transferencia).map(Lancamento::getTransferenciaId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : repositorio.findAllByUsuarioIdAndTransferenciaIdIn(usuarioId, ids).stream()
                .collect(Collectors.groupingBy(Lancamento::getTransferenciaId));
    }

    private static boolean passa(Lancamento lancamento, FiltroDaLista filtro, Set<Long> categoriasDoFiltro, String chaveDaBusca) {
        return (filtro.contaId() == null || filtro.contaId().equals(lancamento.getContaId()))
                && (filtro.cartaoId() == null || filtro.cartaoId().equals(lancamento.getCartaoId()))
                && (filtro.categoriaId() == null || categoriasDoFiltro.contains(lancamento.getCategoriaId()))
                && (!filtro.semCategoria() || lancamento.getCategoriaId() == null)
                && (filtro.tipo() == null || filtro.tipo() == lancamento.getTipo())
                && (chaveDaBusca == null || lancamento.getDescricaoChave().contains(chaveDaBusca));
    }

    /** A transferência não tem categoria: qualquer filtro de categoria a tira (decisão 11). */
    private static boolean passaTransferencia(Lancamento referencia, FiltroDaLista filtro, String chaveDaBusca) {
        return filtro.categoriaId() == null && !filtro.semCategoria()
                && (filtro.tipo() == null || filtro.tipo() == TipoLancamento.TRANSFERENCIA)
                && (chaveDaBusca == null || referencia.getDescricaoChave().contains(chaveDaBusca));
    }

    /** A categoria escolhida e, se for principal, as subcategorias dela (decisão 11). */
    private static Set<Long> idsComSubcategorias(List<CategoriaResumo> todas, Long categoriaId) {
        return todas.stream()
                .filter(categoria -> categoria.id().equals(categoriaId) || categoriaId.equals(categoria.categoriaMaeId()))
                .map(CategoriaResumo::id)
                .collect(Collectors.toSet());
    }

    /** Só receitas e despesas entram (decisão 9); as saídas somam negativo. */
    private static ListaDoMesResponse.Totais totais(List<ItemDaLista> itens) {
        List<BigDecimal> entradas = valores(itens, TipoLancamento.RECEITA);
        List<BigDecimal> saidas = valores(itens, TipoLancamento.DESPESA);
        BigDecimal somaDasEntradas = somar(entradas.stream());
        BigDecimal somaDasSaidas = somar(saidas.stream());
        return new ListaDoMesResponse.Totais(somaDasEntradas, entradas.size(), somaDasSaidas, saidas.size(),
                somaDasEntradas.add(somaDasSaidas));
    }

    private static List<BigDecimal> valores(List<ItemDaLista> itens, TipoLancamento tipo) {
        return itens.stream().filter(item -> item.tipo() == tipo).map(ItemDaLista::valor).toList();
    }

    private static BigDecimal somar(Stream<BigDecimal> valores) {
        return valores.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Com o filtro de uma conta, o saldo dela no fim de cada dia do mês (decisão 7): saldo
     * inicial + tudo o que veio antes do mês + o movimento do mês até o dia, com todos os
     * lançamentos da conta, sem os outros filtros. Sem filtro de conta, vazio.
     */
    private TreeMap<LocalDate, BigDecimal> saldosNoFimDoDia(Long usuarioId, Long contaId, Montagem montagem, YearMonth mes,
                                                             List<Lancamento> doMes) {
        ContaResumo conta = contaId == null ? null : montagem.conta(contaId);
        TreeMap<LocalDate, BigDecimal> saldos = new TreeMap<>();
        if (conta == null) {
            return saldos;
        }
        BigDecimal antes = repositorio.somarDaContaAntesDe(usuarioId, contaId, mes.atDay(1));
        BigDecimal saldo = conta.saldoInicial().add(antes == null ? BigDecimal.ZERO : antes);
        Map<LocalDate, BigDecimal> movimentoPorDia = new TreeMap<>();
        doMes.stream().filter(lancamento -> contaId.equals(lancamento.getContaId()))
                .forEach(lancamento -> movimentoPorDia.merge(lancamento.getData(), lancamento.getValor(), BigDecimal::add));
        saldos.put(mes.atDay(1).minusDays(1), saldo.setScale(2, RoundingMode.UNNECESSARY));
        for (Map.Entry<LocalDate, BigDecimal> dia : movimentoPorDia.entrySet()) {
            saldo = saldo.add(dia.getValue());
            saldos.put(dia.getKey(), saldo.setScale(2, RoundingMode.UNNECESSARY));
        }
        return saldos;
    }

    private static BigDecimal saldoAte(TreeMap<LocalDate, BigDecimal> saldos, LocalDate dia) {
        return saldos.floorEntry(dia).getValue();
    }

    private record Linha(ItemDaLista item, long ordem) {
    }

    /** Os dois lados de uma transferência: a saída é o negativo. */
    private record Par(Lancamento saida, Lancamento entrada) {

        static Par de(List<Lancamento> lados) {
            Lancamento saida = lados.stream().filter(lado -> lado.getValor().signum() < 0).findFirst().orElseThrow();
            Lancamento entrada = lados.stream().filter(lado -> lado.getValor().signum() > 0).findFirst().orElseThrow();
            return new Par(saida, entrada);
        }
    }
}
