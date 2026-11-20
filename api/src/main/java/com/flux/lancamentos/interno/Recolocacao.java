package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Decisões 7 e 12 (ADR 0017): depois que as datas das faturas mudam, as compras e os estornos cuja 1ª parcela
 * passa a cair noutra fatura trocam de fatura com todas as parcelas, cada uma na data do vencimento da nova.
 * O mês da 1ª parcela sai das parcelas gravadas (a compra em andamento não tem a 1ª: conta-se para trás).
 */
@Component
class Recolocacao {

    private final LancamentoRepository lancamentos;
    private final FaturaRepository faturas;
    private final RegrasDosLancamentos regras;

    Recolocacao(LancamentoRepository lancamentos, FaturaRepository faturas, RegrasDosLancamentos regras) {
        this.lancamentos = lancamentos;
        this.faturas = faturas;
        this.regras = regras;
    }

    /** Quantas compras e quantos estornos mudaram de fatura (o aviso do ajuste conta cada um, decisão 12). */
    record Movidas(int compras, int estornos) {
    }

    /**
     * Recoloca as {@code candidatas} pelas datas atuais de {@code doCartao} e devolve quantas compras e quantos
     * estornos mudaram de fatura. Depois de gravar, confere o limite de 1.000 lançamentos nos meses que receberam
     * parcela; a frase começa com {@code quem} ("O ajuste passaria"). Roda na transação e na trava de quem chama.
     */
    Movidas recolocar(Long usuarioId, FaturasDoCartao doCartao, Collection<Compra> candidatas, String quem) {
        if (candidatas.isEmpty()) {
            return new Movidas(0, 0);
        }
        Map<Long, List<Lancamento>> parcelas = lancamentos.findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(usuarioId,
                        candidatas.stream().map(Compra::getId).toList()).stream()
                .collect(Collectors.groupingBy(Lancamento::getCompraId, LinkedHashMap::new, Collectors.toList()));
        Instant agora = regras.agora();
        Map<YearMonth, Long> mesesQueGanharam = new HashMap<>();
        List<Lancamento> movidas = new ArrayList<>();
        int compras = 0;
        int estornos = 0;
        for (Compra compra : candidatas.stream().sorted(Comparator.comparing(Compra::getId)).toList()) {
            List<Lancamento> daCompra = parcelas.getOrDefault(compra.getId(), List.of());
            if (daCompra.isEmpty()) {
                continue;
            }
            Lancamento primeira = daCompra.getFirst();
            YearMonth atual = YearMonth.from(primeira.getData()).minusMonths(primeira.getNumeroParcela() - 1L);
            YearMonth novo = doCartao.faturaDe(compra.getDataCompra());
            if (novo.equals(atual)) {
                continue;
            }
            if (compra.getTipo() == TipoCompra.ESTORNO) {
                estornos++;
            } else {
                compras++;
            }
            for (Lancamento parcela : daCompra) {
                YearMonth mes = novo.plusMonths(parcela.getNumeroParcela() - 1L);
                Fatura fatura = doCartao.obter(faturas, mes, agora);
                parcela.moverParaFatura(fatura.getId(), fatura.getDataVencimento(), agora);
                mesesQueGanharam.put(mes, 0L);
                movidas.add(parcela);
            }
        }
        lancamentos.saveAllAndFlush(movidas);
        regras.exigirLimiteDosMeses(usuarioId, mesesQueGanharam, quem);
        return new Movidas(compras, estornos);
    }
}
