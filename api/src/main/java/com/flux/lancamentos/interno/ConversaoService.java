package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.LancamentosImportados;
import com.flux.lancamentos.interno.dto.ConverterRequest;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.ParesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversão de importados em transferência (Fatia 04c, ADR 0016): os pares possíveis, converter,
 * desfazer e o que a importação pede (os pares sugeridos no resultado, os importados convertidos no
 * histórico e o desfazer da importação de um lado). Pega só a trava de contas, como a transferência
 * (decisão 36): a categoria só sai, e sair não corre com a exclusão dela.
 */
@Service
public class ConversaoService {

    /** No máximo 20 pares na resposta (o mais perto primeiro): a lista é um grupo de botões de opção. */
    static final int LIMITE_DE_PARES = 20;
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final String PAR_QUE_NAO_SERVE =
            "O outro lado precisa ter o mesmo valor, o sinal oposto e estar em outra conta, com até 5 dias de diferença.";

    private final LancamentoRepository lancamentos;
    private final TransferenciaRepository transferencias;
    private final RegrasDosLancamentos regras;
    private final ListaDosLancamentos lista;
    private final ContasConsulta contas;

    public ConversaoService(LancamentoRepository lancamentos, TransferenciaRepository transferencias, RegrasDosLancamentos regras,
                            ListaDosLancamentos lista, ContasConsulta contas) {
        this.lancamentos = lancamentos;
        this.transferencias = transferencias;
        this.regras = regras;
        this.lista = lista;
        this.contas = contas;
    }

    /** Decisões 26 e 27: o importado e os pares possíveis dele. */
    @Transactional(readOnly = true)
    public ParesResponse pares(Long usuarioId, Long id) {
        Lancamento fixo = encontrar(usuarioId, id);
        exigirQueConverte(usuarioId, fixo);
        Map<Long, ContaResumo> doUsuario = contasDoUsuario(usuarioId);
        List<ParesResponse.Par> pares = Pares.de(fixo, janela(usuarioId, fixo.getData(), fixo.getData()), ativas(doUsuario)).stream()
                .limit(LIMITE_DE_PARES)
                .map(par -> new ParesResponse.Par(par.getId(), par.getData(), par.getDescricao(), par.getValor(), par.getContaId(),
                        doUsuario.get(par.getContaId()).nome(), par.getOrigem()))
                .toList();
        return new ParesResponse(lista.itemDoLancamento(usuarioId, fixo), fixo.getData().minusDays(Pares.DIAS),
                fixo.getData().plusDays(Pares.DIAS), pares);
    }

    /**
     * Decisões 26 a 29 e 36: liga o importado a um par ou a um lado novo, lançado na conta dada com a
     * mesma data e o valor oposto. Os dois lados perdem a categoria e ganham a mesma descrição; data,
     * valor, conta, observação, descrição do extrato e chave ficam. Só o lado novo confere os limites.
     */
    @Transactional
    public ItemDaLista converter(Long usuarioId, Long id, ConverterRequest pedido) {
        exigirUmOutroLado(pedido);
        regras.travarContas(usuarioId);
        Lancamento fixo = encontrar(usuarioId, id);
        exigirQueConverte(usuarioId, fixo);
        Lancamento par = pedido.parId() == null ? null : parEscolhido(usuarioId, fixo, pedido.parId());
        if (par == null) {
            exigirContaDoLadoNovo(usuarioId, fixo, pedido.contaId());
        }
        Instant agora = regras.agora();
        Transferencia transferencia = transferencias.save(new Transferencia(usuarioId, agora));
        String descricao = pedido.descricao() == null || pedido.descricao().isEmpty() ? TransferenciaService.DESCRICAO_PADRAO
                : pedido.descricao();
        fixo.converterEmLado(transferencia.getId(), descricao, agora);
        Lancamento outro = par;
        if (outro == null) {
            outro = new Lancamento(usuarioId, transferencia.getId(), agora);
            outro.alterar(TipoLancamento.TRANSFERENCIA, pedido.contaId(), fixo.getData(), descricao, fixo.getValor().negate(), null, null,
                    agora);
        } else {
            outro.converterEmLado(transferencia.getId(), descricao, agora);
        }
        lancamentos.saveAll(List.of(fixo, outro));
        return lista.itemDaTransferencia(usuarioId, List.of(fixo, outro));
    }

    /**
     * Decisões 32 e 33: os dois lados voltam a ser receita e despesa, sem categoria, e o vínculo sai.
     * Só a transferência convertida (com um lado importado) se desfaz; com uma conta arquivada, não.
     * Devolve os dois lançamentos, a saída primeiro.
     */
    @Transactional
    public List<ItemDaLista> desfazer(Long usuarioId, Long transferenciaId) {
        regras.travarContas(usuarioId);
        Transferencia transferencia = transferencias.findByIdAndUsuarioId(transferenciaId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transferência não encontrada."));
        List<Lancamento> lados = lancamentos.findAllByUsuarioIdAndTransferenciaId(usuarioId, transferencia.getId());
        if (!convertida(lados)) {
            throw new RegraDeNegocioException("Esta transferência foi lançada à mão: para tirá-la, exclua a transferência.",
                    "TRANSFERENCIA_MANUAL");
        }
        lados.forEach(lado -> regras.exigirContaAtivaPara(usuarioId, lado.getContaId(), "desfazer esta transferência"));
        soltar(lados, List.of(transferencia));
        return lados.stream().sorted(Comparator.comparing(Lancamento::getValor))
                .map(lado -> lista.itemDoLancamento(usuarioId, lado))
                .toList();
    }

    /**
     * Decisão 34: antes de apagar os lançamentos de uma importação, desfaz as conversões dos que estão
     * numa transferência. O outro lado volta a ser receita ou despesa, sem categoria; com ele numa conta
     * arquivada, o desfazer inteiro é recusado. Roda na transação e na trava de contas do desfazer.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void desfazerDaImportacao(Long usuarioId, Long importacaoId) {
        List<Lancamento> daImportacao = lancamentos.findAllByUsuarioIdAndImportacaoIdAndTransferenciaIdIsNotNull(usuarioId, importacaoId);
        if (daImportacao.isEmpty()) {
            return;
        }
        Map<Long, Lancamento> porTransferencia = daImportacao.stream()
                .collect(Collectors.toMap(Lancamento::getTransferenciaId, Function.identity()));
        List<Lancamento> lados = lancamentos.findAllByUsuarioIdAndTransferenciaIdIn(usuarioId, porTransferencia.keySet());
        Map<Long, ContaResumo> doUsuario = contasDoUsuario(usuarioId);
        lados.stream()
                .filter(lado -> !importacaoId.equals(lado.getImportacaoId()) && doUsuario.get(lado.getContaId()).arquivada())
                .min(Comparator.comparing(Lancamento::getData).thenComparing(Lancamento::getId))
                .ifPresent(lado -> {
                    throw new RegraDeNegocioException("A transferência de "
                            + porTransferencia.get(lado.getTransferenciaId()).getData().format(DIA_E_MES) + " liga esta importação a "
                            + doUsuario.get(lado.getContaId()).nome()
                            + ", que está arquivada. Reative a conta para desfazer esta importação.", "CONTA_ARQUIVADA");
                });
        soltar(lados, transferencias.findAllByIdInAndUsuarioId(porTransferencia.keySet(), usuarioId));
    }

    /**
     * Decisão 35: as linhas da importação que têm um só par possível. Um par que serviria a mais de uma
     * linha fica de fora: converter uma tiraria o par da outra.
     */
    @Transactional(readOnly = true)
    public List<LancamentosImportados.ParSugerido> paresSugeridos(Long usuarioId, Long importacaoId) {
        List<Lancamento> daImportacao =
                lancamentos.findAllByUsuarioIdAndImportacaoIdAndTransferenciaIdIsNullAndIgnoradoEmIsNull(usuarioId, importacaoId);
        if (daImportacao.isEmpty()) {
            return List.of();
        }
        Map<Long, ContaResumo> doUsuario = contasDoUsuario(usuarioId);
        Set<Long> ativas = ativas(doUsuario);
        LocalDate inicio = daImportacao.stream().map(Lancamento::getData).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate fim = daImportacao.stream().map(Lancamento::getData).max(Comparator.naturalOrder()).orElseThrow();
        Map<BigDecimal, List<Lancamento>> porValor = janela(usuarioId, inicio, fim).stream()
                .collect(Collectors.groupingBy(Lancamento::getValor));
        Map<Lancamento, Lancamento> unicos = new LinkedHashMap<>();
        daImportacao.stream()
                .sorted(Comparator.comparing(Lancamento::getData).thenComparing(Lancamento::getId).reversed())
                .forEach(fixo -> {
                    List<Lancamento> pares = Pares.de(fixo, porValor.getOrDefault(fixo.getValor().negate(), List.of()), ativas);
                    if (pares.size() == 1) {
                        unicos.put(fixo, pares.getFirst());
                    }
                });
        Map<Long, Long> vezes = unicos.values().stream().collect(Collectors.groupingBy(Lancamento::getId, Collectors.counting()));
        return unicos.entrySet().stream()
                .filter(sugestao -> vezes.get(sugestao.getValue().getId()) == 1)
                .map(sugestao -> new LancamentosImportados.ParSugerido(ladoSugerido(sugestao.getKey(), doUsuario),
                        ladoSugerido(sugestao.getValue(), doUsuario)))
                .toList();
    }

    /** Decisão 34: os importados que estão numa transferência, com a conta e o tipo que o outro lado volta a ter. */
    @Transactional(readOnly = true)
    public List<LancamentosImportados.Convertido> convertidos(Long usuarioId) {
        List<Lancamento> importados = lancamentos.findAllByUsuarioIdAndImportacaoIdIsNotNullAndTransferenciaIdIsNotNull(usuarioId);
        if (importados.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Lancamento>> lados = lancamentos.findAllByUsuarioIdAndTransferenciaIdIn(usuarioId,
                importados.stream().map(Lancamento::getTransferenciaId).collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(Lancamento::getTransferenciaId));
        return importados.stream()
                .sorted(Comparator.comparing(Lancamento::getData).thenComparing(Lancamento::getId))
                .map(importado -> {
                    Lancamento outro = lados.get(importado.getTransferenciaId()).stream()
                            .filter(lado -> !lado.getId().equals(importado.getId())).findFirst().orElseThrow();
                    return new LancamentosImportados.Convertido(importado.getImportacaoId(), importado.getData(), outro.getContaId(),
                            outro.getValor().signum() > 0 ? TipoLancamento.RECEITA.name() : TipoLancamento.DESPESA.name());
                })
                .toList();
    }

    /** Convertida: algum lado veio de extrato. A transferência lançada à mão tem os dois lados manuais. */
    static boolean convertida(List<Lancamento> lados) {
        return lados.stream().anyMatch(Lancamento::importado);
    }

    /**
     * Lançamento de conta do usuário que não está excluído: o ignorado e o de fatura (Fatia 05a, decisão 27) são um
     * 404, como no resto de /api/lancamentos.
     */
    private Lancamento encontrar(Long usuarioId, Long id) {
        return lancamentos.findByIdAndUsuarioIdAndFaturaIdIsNullAndIgnoradoEmIsNull(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento não encontrado."));
    }

    /** Decisão 26: só um importado, fora de transferência, numa conta ativa. */
    private void exigirQueConverte(Long usuarioId, Lancamento fixo) {
        if (fixo.transferencia()) {
            throw new RegraDeNegocioException("Este lançamento já está numa transferência.", "JA_EM_TRANSFERENCIA");
        }
        if (!fixo.importado()) {
            throw new RegraDeNegocioException("Só um lançamento importado vira transferência por aqui. Para ligar dois lançamentos "
                    + "lançados à mão, exclua-os e crie a transferência.", "SO_IMPORTADO");
        }
        regras.exigirContaAtivaPara(usuarioId, fixo.getContaId(), "converter este lançamento");
    }

    private static void exigirUmOutroLado(ConverterRequest pedido) {
        if (pedido.parId() == null && pedido.contaId() == null) {
            throw new CampoInvalidoException("parId", "Escolha o outro lado da transferência.");
        }
        if (pedido.parId() != null && pedido.contaId() != null) {
            throw new CampoInvalidoException("parId", "Escolha um lançamento ou uma conta, não os dois.");
        }
    }

    /**
     * Decisão 27, conferida de novo dentro da trava: o par inexistente, de outro usuário, excluído, de fatura
     * (Fatia 05a) ou que não serve dá o mesmo 422; o que só não serve porque a conta foi arquivada diz a conta.
     */
    private Lancamento parEscolhido(Long usuarioId, Lancamento fixo, Long parId) {
        Lancamento par = lancamentos.findByIdAndUsuarioIdAndFaturaIdIsNullAndIgnoradoEmIsNull(parId, usuarioId)
                .orElseThrow(() -> new RegraDeNegocioException(PAR_QUE_NAO_SERVE, "PAR_INVALIDO"));
        if (par.transferencia()) {
            throw new RegraDeNegocioException("O lançamento escolhido já está numa transferência.", "PAR_INVALIDO");
        }
        Map<Long, ContaResumo> doUsuario = contasDoUsuario(usuarioId);
        if (Pares.serve(fixo, par, ativas(doUsuario))) {
            return par;
        }
        if (Pares.serve(fixo, par, doUsuario.keySet())) {
            throw new RegraDeNegocioException(doUsuario.get(par.getContaId()).nome()
                    + " está arquivada. Reative a conta para converter este lançamento.", "CONTA_ARQUIVADA");
        }
        throw new RegraDeNegocioException(PAR_QUE_NAO_SERVE, "PAR_INVALIDO");
    }

    /**
     * Decisão 28: a conta do lado novo é outra, do usuário e ativa, e já existe na data; os limites contam +1.
     * Os 422 daqui levam {@code "campo": "contaId"}: a tela põe a mensagem no campo da conta, sem recarregar os
     * pares (o par não mudou).
     */
    private void exigirContaDoLadoNovo(Long usuarioId, Lancamento fixo, Long contaId) {
        if (contaId.equals(fixo.getContaId())) {
            throw new CampoInvalidoException("contaId", "Escolha uma conta diferente da deste lançamento.");
        }
        try {
            ContaResumo conta = regras.contaParaLancar(usuarioId, contaId);
            if (fixo.getData().isBefore(conta.dataSaldoInicial())) {
                throw new RegraDeNegocioException("A data deste lançamento, " + fixo.getData().format(DATA_CURTA)
                        + ", é anterior ao saldo inicial de " + conta.nome() + " (" + conta.dataSaldoInicial().format(DATA_CURTA)
                        + "). Escolha outra conta.", "DATA_ANTES_DO_SALDO_INICIAL");
            }
            regras.exigirLimiteTotal(usuarioId, 1);
            regras.exigirLimiteDoMes(usuarioId, fixo.getData(), 1);
        } catch (RegraDeNegocioException ex) {
            throw ex.com("campo", "contaId");
        }
    }

    /**
     * Solta os lados e só depois apaga os vínculos: o CASCADE de fk_lancamentos_transferencia apagaria
     * os lados ainda ligados (decisão 32). O flush grava os lados antes do DELETE.
     */
    private void soltar(List<Lancamento> lados, List<Transferencia> vinculos) {
        Instant agora = regras.agora();
        lados.forEach(lado -> lado.soltarDaTransferencia(agora));
        lancamentos.saveAllAndFlush(lados);
        transferencias.deleteAll(vinculos);
        transferencias.flush();
    }

    /** Os lançamentos não excluídos com data a até 5 dias de {@code inicio} e de {@code fim}. */
    private List<Lancamento> janela(Long usuarioId, LocalDate inicio, LocalDate fim) {
        return lancamentos.findAllByUsuarioIdAndDataBetweenAndIgnoradoEmIsNull(usuarioId, inicio.minusDays(Pares.DIAS),
                fim.plusDays(Pares.DIAS));
    }

    private Map<Long, ContaResumo> contasDoUsuario(Long usuarioId) {
        return contas.listar(usuarioId).stream().collect(Collectors.toMap(ContaResumo::id, Function.identity()));
    }

    private static Set<Long> ativas(Map<Long, ContaResumo> doUsuario) {
        return doUsuario.values().stream().filter(conta -> !conta.arquivada()).map(ContaResumo::id).collect(Collectors.toSet());
    }

    private static LancamentosImportados.LadoSugerido ladoSugerido(Lancamento lancamento, Map<Long, ContaResumo> doUsuario) {
        return new LancamentosImportados.LadoSugerido(lancamento.getId(), lancamento.getData(), lancamento.getDescricao(),
                lancamento.getValor(), lancamento.getContaId(), doUsuario.get(lancamento.getContaId()).nome());
    }
}
