package com.flux.relatorios.interno;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.relatorios.interno.ConsultasDoRelatorio.MovimentoDoMes;
import com.flux.relatorios.interno.ConsultasDoRelatorio.SaldoNoDia;
import com.flux.relatorios.interno.dto.RelatorioDoMesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * O relatório de um mês (Fatia 06a, ADR 0020). As consultas rodam numa transação só de leitura em REPEATABLE READ: todas
 * veem o mesmo retrato do banco, e os cards, as categorias e os gráficos nunca se contradizem por uma gravação no meio.
 * "Hoje" vem do Clock, no fuso de São Paulo. O mês sem receita nem despesa entra com zero; o saldo do mês mostrado é o do
 * último mês da série (o dia dele é hoje no mês atual).
 */
@Service
public class RelatorioService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ConsultasDoRelatorio consultas;
    private final Clock clock;

    public RelatorioService(ConsultasDoRelatorio consultas, Clock clock) {
        this.consultas = consultas;
        this.clock = clock;
    }

    /** {@code pedido} nulo, fora da faixa ou sem dados: o mês atual. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RelatorioDoMesResponse doMes(Long usuarioId, YearMonth pedido) {
        LocalDate hoje = LocalDate.now(clock);
        YearMonth atual = YearMonth.from(hoje);
        LocalDate primeira = consultas.primeiraData(usuarioId);
        if (primeira == null) {
            RelatorioDoMesResponse.Total vazio = new RelatorioDoMesResponse.Total(ZERO, 0, 0, null, null);
            return new RelatorioDoMesResponse(atual.toString(), atual.toString(), null, hoje, List.of(),
                    new RelatorioDoMesResponse.Saldo(hoje, true, null, null, 0, 0), vazio, vazio, ZERO, List.of(), List.of());
        }
        JanelaDoRelatorio janela = JanelaDoRelatorio.de(pedido, YearMonth.from(primeira), atual);
        Map<YearMonth, MovimentoDoMes> movimento = consultas.movimentoPorMes(usuarioId, janela.inicio(), janela.mes()).stream()
                .collect(Collectors.toMap(MovimentoDoMes::mes, Function.identity()));
        List<SaldoNoDia> saldos = consultas.saldosNoFimDosMeses(usuarioId, janela.inicio(), janela.mes(), hoje);
        List<RelatorioDoMesResponse.Mes> meses = new ArrayList<>();
        for (SaldoNoDia saldo : saldos) {
            BigDecimal receitas = receitas(movimento.get(saldo.mes()));
            BigDecimal despesas = despesas(movimento.get(saldo.mes()));
            meses.add(new RelatorioDoMesResponse.Mes(saldo.mes().toString(), receitas, despesas, receitas.subtract(despesas),
                    saldo.dia(), saldo.saldo()));
        }
        BigDecimal receitasDaMedia = ZERO;
        BigDecimal despesasDaMedia = ZERO;
        for (YearMonth anterior : janela.media()) {
            receitasDaMedia = receitasDaMedia.add(receitas(movimento.get(anterior)));
            despesasDaMedia = despesasDaMedia.add(despesas(movimento.get(anterior)));
        }
        MovimentoDoMes doMes = movimento.get(janela.mes());
        BigDecimal receitas = receitas(doMes);
        BigDecimal despesas = despesas(doMes);
        BigDecimal mediaDasReceitas = Comparacao.media(receitasDaMedia, janela.media().size());
        BigDecimal mediaDasDespesas = Comparacao.media(despesasDaMedia, janela.media().size());
        SaldoNoDia saldo = saldos.getLast();
        return new RelatorioDoMesResponse(janela.mes().toString(), atual.toString(), YearMonth.from(primeira).toString(), hoje,
                janela.media().stream().map(YearMonth::toString).toList(),
                new RelatorioDoMesResponse.Saldo(saldo.dia(), janela.mes().equals(atual), saldo.saldo(), saldo.investimentos(),
                        saldo.contas(), saldo.contasDeInvestimento()),
                new RelatorioDoMesResponse.Total(receitas, doMes == null ? 0 : doMes.quantidadeDeReceitas(), 0, mediaDasReceitas,
                        Comparacao.de(receitas, mediaDasReceitas)),
                new RelatorioDoMesResponse.Total(despesas, doMes == null ? 0 : doMes.quantidadeDeDespesas(),
                        doMes == null ? 0 : doMes.despesasNoCartao(), mediaDasDespesas, Comparacao.de(despesas, mediaDasDespesas)),
                receitas.subtract(despesas),
                GastosPorCategoria.montar(consultas.despesasPorCategoria(usuarioId, janela.inicioDaMedia(), janela.mes()),
                        consultas.categoriasDeDespesa(usuarioId), janela.mes(), janela.media()),
                List.copyOf(meses));
    }

    private static BigDecimal receitas(MovimentoDoMes movimento) {
        return movimento == null ? ZERO : movimento.receitas();
    }

    private static BigDecimal despesas(MovimentoDoMes movimento) {
        return movimento == null ? ZERO : movimento.despesas();
    }
}
