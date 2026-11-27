package com.flux.projecao.interno;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.contas.MovimentoDasContas;
import com.flux.lancamentos.LancamentosDaProjecao;
import com.flux.lancamentos.LancamentosDaProjecao.CompraPrevista;
import com.flux.lancamentos.LancamentosDaProjecao.FaturaProjetada;
import com.flux.lancamentos.LancamentosDaProjecao.LancamentoFuturo;
import com.flux.projecao.interno.CalculoDaProjecao.Dia;
import com.flux.projecao.interno.CalculoDaProjecao.Entrada;
import com.flux.projecao.interno.CalculoDaProjecao.Resultado;
import com.flux.projecao.interno.CalculoDaProjecao.Resumo;
import com.flux.projecao.interno.dto.AvisoDaProjecaoResponse;
import com.flux.projecao.interno.dto.ProjecaoResponse;
import com.flux.recorrencias.OcorrenciaPrevista;
import com.flux.recorrencias.RecorrenciasConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjecaoService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ContasConsulta contas;
    private final MovimentoDasContas movimento;
    private final CartoesConsulta cartoes;
    private final LancamentosDaProjecao lancamentos;
    private final RecorrenciasConsulta recorrencias;
    private final Clock clock;

    public ProjecaoService(ContasConsulta contas, MovimentoDasContas movimento, CartoesConsulta cartoes,
                           LancamentosDaProjecao lancamentos, RecorrenciasConsulta recorrencias, Clock clock) {
        this.contas = contas;
        this.movimento = movimento;
        this.cartoes = cartoes;
        this.lancamentos = lancamentos;
        this.recorrencias = recorrencias;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProjecaoResponse projetar(Long usuarioId, String ate, String contasPedidas) {
        Calculada calculada = calcular(usuarioId, ate, contasPedidas);
        return resposta(calculada);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AvisoDaProjecaoResponse aviso(Long usuarioId) {
        Calculada calculada = calcular(usuarioId, null, null);
        List<AvisoDaProjecaoResponse.ContaNegativa> negativas = new ArrayList<>();
        for (ContaResumo conta : calculada.escolhidas()) {
            Resumo resumo = calculada.resultado().porConta().get(conta.id());
            if (!resumo.trechos().isEmpty()) {
                negativas.add(new AvisoDaProjecaoResponse.ContaNegativa(conta.id(), conta.nome(), resumo.hoje(),
                        resumo.trechos().getFirst().de(), resumo.menor(), resumo.diaDoMenor()));
            }
        }
        negativas.sort(Comparator.comparing(AvisoDaProjecaoResponse.ContaNegativa::primeiroNegativo)
                .thenComparing(AvisoDaProjecaoResponse.ContaNegativa::contaNome, TextoComparavel.ordemAlfabetica())
                .thenComparing(AvisoDaProjecaoResponse.ContaNegativa::contaId));
        return new AvisoDaProjecaoResponse(List.copyOf(negativas));
    }

    private Calculada calcular(Long usuarioId, String atePedida, String contasPedidas) {
        PedidoDaProjecao.Periodo periodo = PedidoDaProjecao.periodo(LocalDate.now(clock), atePedida);
        List<ContaResumo> todas = contas.listar(usuarioId);
        List<ContaResumo> ativas = todas.stream().filter(conta -> !conta.arquivada())
                .sorted(Comparator.comparing(ContaResumo::nome, TextoComparavel.ordemAlfabetica()).thenComparing(ContaResumo::id))
                .toList();
        List<Long> idsEscolhidos = PedidoDaProjecao.escolhidas(contasPedidas, ativas);
        Map<Long, ContaResumo> todasPorId = todas.stream()
                .collect(Collectors.toMap(ContaResumo::id, Function.identity()));
        Map<Long, ContaResumo> escolhidasPorId = new LinkedHashMap<>();
        for (ContaResumo conta : ativas) {
            if (idsEscolhidos.contains(conta.id())) {
                escolhidasPorId.put(conta.id(), conta);
            }
        }
        Map<Long, BigDecimal> saldosCalculados = movimento.somaPorContaAte(usuarioId, periodo.hoje());
        Map<Long, BigDecimal> saldosDeHoje = new LinkedHashMap<>();
        escolhidasPorId.forEach((id, conta) -> saldosDeHoje.put(id,
                escala(conta.saldoInicial().add(saldosCalculados.getOrDefault(id, ZERO)))));
        List<OcorrenciaPrevista> previstas = recorrencias.previstas(usuarioId, periodo.hoje().minusDays(60), periodo.ate());
        List<CartaoResumo> todosCartoes = cartoes.listar(usuarioId);
        List<CartaoResumo> cartoesElegiveis = todosCartoes.stream().filter(cartao -> !cartao.arquivado())
                .filter(cartao -> contaElegivelParaFatura(todasPorId.get(cartao.contaPagamentoId()), escolhidasPorId)).toList();
        List<CompraPrevista> comprasPrevistas = previstas.stream().filter(prevista -> prevista.cartaoId() != null)
                .filter(prevista -> cartoesElegiveis.stream().anyMatch(cartao -> cartao.id().equals(prevista.cartaoId())))
                .sorted(Comparator.comparing(OcorrenciaPrevista::data)
                        .thenComparing(OcorrenciaPrevista::descricao, TextoComparavel.ordemAlfabetica())
                        .thenComparing(OcorrenciaPrevista::recorrenciaId))
                .map(prevista -> new CompraPrevista(prevista.recorrenciaId(), prevista.periodo(), prevista.cartaoId(), prevista.data(),
                        prevista.valorPrevisto()))
                .toList();
        List<FaturaProjetada> faturas = lancamentos.faturas(usuarioId, cartoesElegiveis, periodo.ate(), comprasPrevistas);
        List<LancamentoFuturo> futuros = lancamentos.futuros(usuarioId, idsEscolhidos, periodo.hoje().plusDays(1), periodo.ate());
        Map<Long, CartaoResumo> cartoesPorId = todosCartoes.stream().collect(Collectors.toMap(CartaoResumo::id, Function.identity()));
        Map<String, OcorrenciaPrevista> previstaPorChave = previstas.stream()
                .collect(Collectors.toMap(ProjecaoService::chave, Function.identity(), (primeira, segunda) -> primeira));
        List<Entrada<ItemInterno>> entradas = entradas(periodo, escolhidasPorId, todasPorId, previstas, faturas, futuros,
                cartoesPorId, previstaPorChave);
        Resultado<ItemInterno> resultado = CalculoDaProjecao.calcular(periodo.hoje(), periodo.ate(), saldosDeHoje, entradas);
        List<ContaResumo> escolhidas = idsEscolhidos.stream().map(escolhidasPorId::get).toList();
        return new Calculada(periodo, ativas, escolhidas, resultado, faturasFora(faturas, cartoesPorId, todasPorId));
    }

    private static boolean contaElegivelParaFatura(ContaResumo conta, Map<Long, ContaResumo> escolhidas) {
        return conta != null && (conta.arquivada() || escolhidas.containsKey(conta.id()));
    }

    private static List<Entrada<ItemInterno>> entradas(PedidoDaProjecao.Periodo periodo,
                                                        Map<Long, ContaResumo> escolhidas,
                                                        Map<Long, ContaResumo> todas,
                                                        List<OcorrenciaPrevista> previstas,
                                                        List<FaturaProjetada> faturas,
                                                        List<LancamentoFuturo> futuros,
                                                        Map<Long, CartaoResumo> cartoes,
                                                        Map<String, OcorrenciaPrevista> previstaPorChave) {
        List<Entrada<ItemInterno>> resultado = new ArrayList<>();
        for (OcorrenciaPrevista prevista : previstas) {
            if (prevista.contaId() != null && escolhidas.containsKey(prevista.contaId())) {
                resultado.add(new Entrada<>(prevista.contaId(), maximo(prevista.data(), periodo.hoje()), prevista.valor(), true,
                        ItemInterno.previsto(prevista)));
            }
        }
        for (FaturaProjetada fatura : faturas) {
            CartaoResumo cartao = cartoes.get(fatura.cartaoId());
            ContaResumo conta = cartao == null ? null : todas.get(cartao.contaPagamentoId());
            if (conta != null && escolhidas.containsKey(conta.id())) {
                resultado.add(new Entrada<>(conta.id(), maximo(fatura.vencimento(), periodo.hoje()), fatura.saida().negate(), true,
                        ItemInterno.fatura(fatura, cartao, conta, previstaPorChave)));
            }
        }
        for (LancamentoFuturo futuro : futuros) {
            ContaResumo conta = todas.get(futuro.contaId());
            ContaResumo outra = futuro.outraContaId() == null ? null : todas.get(futuro.outraContaId());
            boolean interna = "TRANSFERENCIA".equals(futuro.tipo()) && escolhidas.containsKey(futuro.outraContaId());
            resultado.add(new Entrada<>(futuro.contaId(), futuro.data(), futuro.valor(), !interna,
                    ItemInterno.lancamento(futuro, conta, outra)));
        }
        resultado.sort(ordemDasEntradas(periodo.hoje()));
        return List.copyOf(resultado);
    }

    private static Comparator<Entrada<ItemInterno>> ordemDasEntradas(LocalDate hoje) {
        return Comparator.comparing(Entrada<ItemInterno>::dia)
                .thenComparingInt(entrada -> ordemDoTipo(entrada.item(), entrada.dia().equals(hoje)))
                .thenComparing(entrada -> entrada.item(), ProjecaoService::compararItens);
    }

    private static int ordemDoTipo(ItemInterno item, boolean pendencia) {
        if (pendencia) {
            return "FATURA".equals(item.tipo()) ? 0 : 1;
        }
        return switch (item.tipo()) {
            case "PREVISTO" -> 0;
            case "LANCAMENTO" -> 1;
            case "TRANSFERENCIA" -> 2;
            default -> 3;
        };
    }

    private static int compararItens(ItemInterno primeiro, ItemInterno segundo) {
        if ("FATURA".equals(primeiro.tipo()) && "FATURA".equals(segundo.tipo())) {
            int vencimento = primeiro.vencimento().compareTo(segundo.vencimento());
            if (vencimento != 0) {
                return vencimento;
            }
            int cartao = TextoComparavel.ordemAlfabetica().compare(primeiro.cartaoNome(), segundo.cartaoNome());
            return cartao != 0 ? cartao : primeiro.mes().compareTo(segundo.mes());
        }
        if ("PREVISTO".equals(primeiro.tipo()) && "PREVISTO".equals(segundo.tipo())) {
            int descricao = TextoComparavel.ordemAlfabetica().compare(primeiro.descricao(), segundo.descricao());
            return descricao != 0 ? descricao : primeiro.ocorrencia().recorrenciaId().compareTo(segundo.ocorrencia().recorrenciaId());
        }
        Long primeiroId = primeiro.lancamentoId();
        Long segundoId = segundo.lancamentoId();
        if (primeiroId != null && segundoId != null) {
            return primeiroId.compareTo(segundoId);
        }
        return primeiro.tipo().compareTo(segundo.tipo());
    }

    private static List<ProjecaoResponse.FaturaFora> faturasFora(List<FaturaProjetada> faturas,
                                                                  Map<Long, CartaoResumo> cartoes,
                                                                  Map<Long, ContaResumo> contas) {
        Map<Long, List<FaturaProjetada>> porCartao = faturas.stream().filter(fatura -> {
            CartaoResumo cartao = cartoes.get(fatura.cartaoId());
            ContaResumo conta = cartao == null ? null : contas.get(cartao.contaPagamentoId());
            return conta != null && conta.arquivada();
        }).collect(Collectors.groupingBy(FaturaProjetada::cartaoId));
        List<ProjecaoResponse.FaturaFora> resultado = new ArrayList<>();
        for (Map.Entry<Long, List<FaturaProjetada>> entrada : porCartao.entrySet()) {
            CartaoResumo cartao = cartoes.get(entrada.getKey());
            ContaResumo conta = contas.get(cartao.contaPagamentoId());
            List<FaturaProjetada> doCartao = entrada.getValue().stream().sorted(Comparator.comparing(FaturaProjetada::mes)).toList();
            BigDecimal valor = doCartao.stream().map(FaturaProjetada::saida).reduce(ZERO, BigDecimal::add);
            resultado.add(new ProjecaoResponse.FaturaFora(cartao.id(), cartao.nome(), conta.id(), conta.nome(),
                    doCartao.stream().map(fatura -> fatura.mes().toString()).toList(), escala(valor)));
        }
        resultado.sort(Comparator.comparing(ProjecaoResponse.FaturaFora::cartaoNome, TextoComparavel.ordemAlfabetica()));
        return List.copyOf(resultado);
    }

    private static ProjecaoResponse resposta(Calculada calculada) {
        PedidoDaProjecao.Periodo periodo = calculada.periodo();
        List<Long> escolhidas = calculada.escolhidas().stream().map(ContaResumo::id).toList();
        List<ProjecaoResponse.Conta> contas = calculada.ativas().stream().map(conta -> new ProjecaoResponse.Conta(conta.id(),
                conta.nome(), conta.tipo(), conta.cor(), !"INVESTIMENTO".equals(conta.tipo()), escolhidas.contains(conta.id()))).toList();
        List<ProjecaoResponse.ResumoDaConta> porConta = new ArrayList<>();
        for (ContaResumo conta : calculada.escolhidas()) {
            Resumo resumo = calculada.resultado().porConta().get(conta.id());
            porConta.add(resumoDaConta(conta, resumo));
        }
        return new ProjecaoResponse(periodo.hoje(), periodo.ate(), (int) ChronoUnit.DAYS.between(periodo.hoje(), periodo.ate()),
                new ProjecaoResponse.Periodos(periodo.padrao(), periodo.tresMeses(), periodo.seisMeses(), periodo.dozeMeses()), contas,
                escolhidas, resumo(calculada.resultado().total()), List.copyOf(porConta),
                calculada.resultado().pendencias().stream().map(item -> item.resposta(true)).toList(),
                calculada.resultado().dias().stream().map(ProjecaoService::dia).toList(), calculada.faturasFora());
    }

    private static ProjecaoResponse.Dia dia(Dia<ItemInterno> dia) {
        return new ProjecaoResponse.Dia(dia.data(), dia.movimento(), dia.saldo(), dia.itens().stream().map(item -> item.resposta(false))
                .toList());
    }

    private static ProjecaoResponse.Resumo resumo(Resumo resumo) {
        return new ProjecaoResponse.Resumo(resumo.hoje(), resumo.pendencias(), resumo.inicio(), resumo.menor(), resumo.diaDoMenor(),
                resumo.fim(), resumo.diasNegativos(), resumo.trechos().stream().map(trecho -> new ProjecaoResponse.Trecho(trecho.de(),
                        trecho.ate())).toList());
    }

    private static ProjecaoResponse.ResumoDaConta resumoDaConta(ContaResumo conta, Resumo resumo) {
        return new ProjecaoResponse.ResumoDaConta(conta.id(), conta.nome(), conta.cor(), resumo.hoje(), resumo.pendencias(),
                resumo.inicio(), resumo.menor(), resumo.diaDoMenor(), resumo.fim(), resumo.diasNegativos(),
                resumo.trechos().stream().map(trecho -> new ProjecaoResponse.Trecho(trecho.de(), trecho.ate())).toList());
    }

    private static String chave(OcorrenciaPrevista prevista) {
        return prevista.recorrenciaId() + ":" + prevista.periodo();
    }

    private static LocalDate maximo(LocalDate primeira, LocalDate segunda) {
        return primeira.isAfter(segunda) ? primeira : segunda;
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2);
    }

    private record Calculada(PedidoDaProjecao.Periodo periodo, List<ContaResumo> ativas, List<ContaResumo> escolhidas,
                             Resultado<ItemInterno> resultado, List<ProjecaoResponse.FaturaFora> faturasFora) {
    }

    private record ItemInterno(String tipo, LocalDate data, String descricao, BigDecimal valor, boolean valorVaria,
                               Long contaId, String contaNome, Long lancamentoId, Long outraContaId, String outraContaNome,
                               Long cartaoId, String cartaoNome, String cartaoCor, String mes, LocalDate vencimento,
                               BigDecimal pendente, BigDecimal previsto, List<PrevistoInterno> previstos,
                               OcorrenciaPrevista ocorrencia) {

        private static ItemInterno previsto(OcorrenciaPrevista prevista) {
            return new ItemInterno("PREVISTO", prevista.data(), prevista.descricao(), prevista.valor(), prevista.valorVaria(),
                    prevista.contaId(), prevista.contaNome(), null, null, null, prevista.cartaoId(), prevista.cartaoNome(),
                    prevista.cartaoCor(), null, null, null, null, List.of(), prevista);
        }

        private static ItemInterno lancamento(LancamentoFuturo lancamento, ContaResumo conta, ContaResumo outraConta) {
            String tipo = "TRANSFERENCIA".equals(lancamento.tipo()) ? "TRANSFERENCIA" : "LANCAMENTO";
            return new ItemInterno(tipo, lancamento.data(), lancamento.descricao(), lancamento.valor(), false, lancamento.contaId(),
                    conta == null ? null : conta.nome(), lancamento.id(), lancamento.outraContaId(),
                    outraConta == null ? null : outraConta.nome(), null, null, null, null, null, null, null, List.of(), null);
        }

        private static ItemInterno fatura(FaturaProjetada fatura, CartaoResumo cartao, ContaResumo conta,
                                          Map<String, OcorrenciaPrevista> ocorrencias) {
            List<PrevistoInterno> previstas = fatura.previstas().stream().map(prevista -> new PrevistoInterno(prevista,
                    ocorrencias.get(prevista.recorrenciaId() + ":" + prevista.periodo()))).toList();
            return new ItemInterno("FATURA", fatura.vencimento(), null, fatura.saida().negate(), false, conta.id(), conta.nome(),
                    null, null, null, cartao.id(), cartao.nome(), cartao.cor(), fatura.mes().toString(), fatura.vencimento(),
                    fatura.pendente(), fatura.previsto(), previstas, null);
        }

        private ProjecaoResponse.Item resposta(boolean comOcorrencia) {
            return new ProjecaoResponse.Item(tipo, data, descricao, escala(valor), valorVaria, contaId, contaNome, lancamentoId,
                    outraContaId, outraContaNome, cartaoId, cartaoNome, cartaoCor, mes, vencimento,
                    pendente == null ? null : escala(pendente),
                    previsto == null ? null : escala(previsto), previstos.stream().map(item -> item.resposta(comOcorrencia)).toList(),
                    comOcorrencia ? ProjecaoService.ocorrencia(ocorrencia) : null);
        }
    }

    private record PrevistoInterno(CompraPrevista previsto, OcorrenciaPrevista ocorrencia) {

        private ProjecaoResponse.PrevistoDaFatura resposta(boolean comOcorrencia) {
            return new ProjecaoResponse.PrevistoDaFatura(previsto.recorrenciaId(), previsto.periodo(), previsto.data(),
                    ocorrencia == null ? null : ocorrencia.descricao(), escala(previsto.valor()),
                    comOcorrencia ? ProjecaoService.ocorrencia(ocorrencia) : null);
        }
    }

    private static ProjecaoResponse.OcorrenciaDaProjecao ocorrencia(OcorrenciaPrevista prevista) {
        if (prevista == null) {
            return null;
        }
        return new ProjecaoResponse.OcorrenciaDaProjecao(prevista.recorrenciaId(), prevista.periodo(), prevista.data(), null,
                prevista.descricao(), prevista.tipo(), escala(prevista.valor()), escala(prevista.valorPrevisto()), prevista.valorVaria(),
                prevista.contaId(), prevista.contaNome(), prevista.cartaoId(), prevista.cartaoNome(), prevista.cartaoCor(),
                prevista.categoriaId(), prevista.categoriaNome(), prevista.categoriaMaeNome(), prevista.atrasada(), prevista.visivelAte());
    }
}
