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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.compartilhado.erro.RegraDeNegocioException;
import org.springframework.stereotype.Component;

/**
 * Decisões 7 e 12 (ADR 0017): depois que as datas das faturas mudam, as compras e os estornos cuja 1ª parcela
 * passa a cair noutra fatura trocam de fatura com todas as parcelas, cada uma na data do vencimento da nova.
 * O mês da 1ª parcela sai das parcelas gravadas (a compra em andamento não tem a 1ª: conta-se para trás). Desde a Fatia
 * 05b (decisão 35), o ajuste não move compra que tem parcela numa fatura paga, nem para uma fatura paga.
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
     * Fatia 05b (decisão 35): os meses das faturas pagas, que nenhuma compra deixa nem recebe. {@code quem} começa a frase
     * ("O ajuste moveria"), e {@code nomeDoMes} diz o mês ("novembro").
     */
    record Pagas(Set<YearMonth> meses, String quem, Function<YearMonth, String> nomeDoMes) {

        static final Pagas NENHUMA = new Pagas(Set.of(), "", mes -> "");
    }

    /** A troca de dias (decisão 7) não precisa da trava da fatura paga: ela só move compras para as faturas seguintes à aberta. */
    Movidas recolocar(Long usuarioId, FaturasDoCartao doCartao, Collection<Compra> candidatas, String quem) {
        return recolocar(usuarioId, doCartao, candidatas, quem, Pagas.NENHUMA);
    }

    /**
     * Recoloca as {@code candidatas} pelas datas atuais de {@code doCartao} e devolve quantas compras e quantos
     * estornos mudaram de fatura. Depois de gravar, confere o limite de 1.000 lançamentos nos meses que receberam
     * parcela; a frase começa com {@code quem} ("O ajuste passaria"). Roda na transação e na trava de quem chama.
     */
    Movidas recolocar(Long usuarioId, FaturasDoCartao doCartao, Collection<Compra> candidatas, String quem, Pagas pagas) {
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
            if (compra.importada()) {
                continue;
            }
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
            exigirForaDasPagas(compra, daCompra, novo, pagas);
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

    /**
     * Decisão 35: "O ajuste moveria Sapataria Passo Firme, que tem parcela na fatura de novembro, já paga." ou, para uma
     * fatura paga, "O ajuste moveria Sapataria Passo Firme para a fatura de dezembro, já paga.". Recusa antes de mover.
     */
    private static void exigirForaDasPagas(Compra compra, List<Lancamento> daCompra, YearMonth novo, Pagas pagas) {
        if (pagas.meses().isEmpty()) {
            return;
        }
        String descricao = daCompra.getFirst().getDescricao();
        Optional<YearMonth> deUmaPaga = daCompra.stream().map(parcela -> YearMonth.from(parcela.getData())).filter(pagas.meses()::contains)
                .min(YearMonth::compareTo);
        if (deUmaPaga.isPresent()) {
            throw new RegraDeNegocioException(pagas.quem() + " " + descricao + ", que " + (compra.estorno() ? "está" : "tem parcela")
                    + " na fatura de " + pagas.nomeDoMes().apply(deUmaPaga.get()) + ", já paga.", "FATURA_PAGA");
        }
        daCompra.stream().map(parcela -> novo.plusMonths(parcela.getNumeroParcela() - 1L)).filter(pagas.meses()::contains)
                .min(YearMonth::compareTo)
                .ifPresent(paga -> {
                    throw new RegraDeNegocioException(pagas.quem() + " " + descricao + " para a fatura de " + pagas.nomeDoMes().apply(paga)
                            + ", já paga.", "FATURA_PAGA");
                });
    }
}
