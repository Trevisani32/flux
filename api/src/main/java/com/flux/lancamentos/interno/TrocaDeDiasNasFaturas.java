package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.TrocaDeDias;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisão 7 (ADR 0017): os dias novos valem da fatura seguinte à aberta em diante. Da véspera do mês mais antigo
 * entre a primeira fatura com lançamento e o mês de 10 anos atrás até a aberta, as que ainda não existem são gravadas
 * com as datas de agora (assim nenhuma data que tem ou ainda pode ter lançamento muda de fatura); as futuras gravadas
 * voltam às datas dos dias novos (o ON UPDATE CASCADE leva a data das parcelas junto) e as compras com data depois do
 * fechamento da aberta trocam de fatura se for o caso. No cartão vazio, sem lançamento e sem fatura com o fechamento
 * ajustado, os dias novos valem já, inclusive na aberta, e as faturas que restaram, sem nada dentro, saem (exceção das
 * decisões 7 e 9, aprovada pelo dono).
 */
@Component
public class TrocaDeDiasNasFaturas implements TrocaDeDias {

    private final FaturaRepository faturas;
    private final LancamentoRepository lancamentos;
    private final CompraRepository compras;
    private final Recolocacao recolocacao;
    private final RegrasDosLancamentos regras;

    public TrocaDeDiasNasFaturas(FaturaRepository faturas, LancamentoRepository lancamentos, CompraRepository compras, Recolocacao recolocacao,
                                 RegrasDosLancamentos regras) {
        this.faturas = faturas;
        this.lancamentos = lancamentos;
        this.compras = compras;
        this.recolocacao = recolocacao;
        this.regras = regras;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Previa> previa(Long usuarioId, CartaoResumo cartao, int diaFechamento, int diaVencimento, LocalDate hoje) {
        FaturasDoCartao antes = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        if (vazio(antes, lancamentos.totaisDoCartao(usuarioId, cartao.id()))) {
            return Optional.empty();
        }
        YearMonth aberta = antes.faturaDe(hoje);
        CalendarioDoCartao.Datas datasDaAberta = antes.datas(aberta);
        Map<YearMonth, CalendarioDoCartao.Datas> base = new HashMap<>();
        antes.gravadas().forEach((mes, datas) -> {
            if (mes.isBefore(aberta)) {
                base.put(mes, datas);
            }
        });
        base.put(aberta, datasDaAberta);
        YearMonth primeira = aberta.plusMonths(1);
        CalendarioDoCartao.Datas datasDaPrimeira = new CalendarioDoCartao(diaFechamento, diaVencimento).datas(primeira, base);
        return Optional.of(new Previa(aberta, datasDaAberta.fechamento(), primeira, datasDaAberta.fechamento().plusDays(1),
                datasDaPrimeira.fechamento(), datasDaPrimeira.vencimento()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void aplicar(Long usuarioId, CartaoResumo cartao, int diaFechamento, int diaVencimento, LocalDate hoje) {
        FaturasDoCartao antes = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        List<TotalDaFatura> totais = lancamentos.totaisDoCartao(usuarioId, cartao.id());
        if (vazio(antes, totais)) {
            // As faturas que restaram (de compras excluídas, de um ajuste desfeito ou de uma troca anterior) não têm nada
            // dentro: saem, dentro da trava de cartões, e a próxima compra grava as dela com os dias novos. Nenhum
            // lançamento aponta para elas; se apontasse, a FK recusaria.
            faturas.deleteAll(antes.porMes().values());
            faturas.flush();
            return;
        }
        Instant agora = regras.agora();
        YearMonth aberta = antes.faturaDe(hoje);
        // A aberta e as anteriores ficam como estão: grava, com as datas de agora, da véspera do mês mais antigo entre
        // a primeira fatura com lançamento e o mês de 10 anos atrás (a data mais antiga que ainda se lança, decisão 16)
        // até a aberta. Assim o período de cada fatura que tem lançamento não muda com os dias novos, e a compra
        // esquecida lançada depois num mês passado cai pelos dias de antes. As gravadas vazias (de um ajuste, ou de uma
        // troca anterior) não puxam o começo para trás, e os meses já gravados não contam: a primeira troca grava no
        // máximo uns 124 meses (mais os que a primeira compra ficou para trás da janela), e as seguintes, os meses
        // desde a anterior.
        Set<Long> comLancamento = totais.stream().map(TotalDaFatura::faturaId).collect(Collectors.toSet());
        YearMonth janela = YearMonth.from(hoje.minusYears(CompraService.ANOS_PARA_TRAS));
        YearMonth primeira = antes.porMes().entrySet().stream().filter(entrada -> comLancamento.contains(entrada.getValue().getId()))
                .map(Map.Entry::getKey).filter(mes -> mes.isBefore(janela)).min(YearMonth::compareTo).orElse(janela);
        for (YearMonth mes = primeira.minusMonths(1); !mes.isAfter(aberta); mes = mes.plusMonths(1)) {
            antes.obter(faturas, mes, agora);
        }
        LocalDate fechamentoDaAberta = antes.datas(aberta).fechamento();
        CalendarioDoCartao novo = new CalendarioDoCartao(diaFechamento, diaVencimento);
        novo.recalcular(aberta, antes.gravadas()).forEach((mes, datas) -> antes.gravada(mes).orElseThrow().recalcular(datas));
        // As datas novas vão para o banco antes de ler as parcelas: o CASCADE muda a data delas.
        faturas.flush();
        CartaoResumo comOsDiasNovos = new CartaoResumo(cartao.id(), cartao.nome(), cartao.cor(), cartao.limite(), diaFechamento,
                diaVencimento, cartao.contaPagamentoId(), cartao.arquivado());
        FaturasDoCartao depois = new FaturasDoCartao(usuarioId, comOsDiasNovos, antes.porMes().values());
        recolocacao.recolocar(usuarioId, depois,
                compras.findAllByUsuarioIdAndCartaoIdAndDataCompraAfter(usuarioId, cartao.id(), fechamentoDaAberta), "Os dias novos passariam");
    }

    /**
     * Exceção da decisão 7: o cartão vazio, sem nenhum lançamento (parcela ou estorno) e sem nenhuma fatura com o
     * fechamento ajustado. A fatura gravada só por uma compra que foi excluída, por uma troca ou por um ajuste desfeito
     * ("Voltar ao fechamento normal") não conta.
     */
    private static boolean vazio(FaturasDoCartao doCartao, List<TotalDaFatura> totais) {
        return totais.isEmpty() && doCartao.porMes().values().stream().noneMatch(Fatura::isFechamentoAjustado);
    }
}
