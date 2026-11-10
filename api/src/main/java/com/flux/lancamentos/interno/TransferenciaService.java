package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.contas.ContaResumo;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.SalvarTransferenciaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transferências (ADR 0004 e ADR 0013): cada uma é um vínculo e dois lançamentos, a saída
 * na origem e a entrada no destino, sempre gravados, editados e excluídos juntos.
 */
@Service
public class TransferenciaService {

    static final String DESCRICAO_PADRAO = "Transferência";

    private final TransferenciaRepository transferencias;
    private final LancamentoRepository lancamentos;
    private final RegrasDosLancamentos regras;
    private final ListaDosLancamentos lista;

    public TransferenciaService(TransferenciaRepository transferencias, LancamentoRepository lancamentos,
                                RegrasDosLancamentos regras, ListaDosLancamentos lista) {
        this.transferencias = transferencias;
        this.lancamentos = lancamentos;
        this.regras = regras;
        this.lista = lista;
    }

    @Transactional(readOnly = true)
    public ItemDaLista buscar(Long usuarioId, Long id) {
        return lista.itemDaTransferencia(usuarioId, lados(usuarioId, encontrar(usuarioId, id)));
    }

    @Transactional
    public ItemDaLista criar(Long usuarioId, SalvarTransferenciaRequest pedido) {
        exigirContasDiferentes(pedido);
        regras.exigirDataNoLimite(pedido.data());
        regras.travarContas(usuarioId);
        ContaResumo origem = regras.contaParaLancar(usuarioId, pedido.contaOrigemId());
        ContaResumo destino = regras.contaParaLancar(usuarioId, pedido.contaDestinoId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(origem, destino));
        regras.exigirLimiteTotal(usuarioId, 2);
        regras.exigirLimiteDoMes(usuarioId, pedido.data(), 2);
        Instant agora = regras.agora();
        Transferencia transferencia = transferencias.save(new Transferencia(usuarioId, agora));
        Lancamento saida = new Lancamento(usuarioId, transferencia.getId(), agora);
        Lancamento entrada = new Lancamento(usuarioId, transferencia.getId(), agora);
        aplicar(saida, entrada, pedido, agora);
        lancamentos.saveAll(List.of(saida, entrada));
        return lista.itemDaTransferencia(usuarioId, List.of(saida, entrada));
    }

    @Transactional
    public ItemDaLista atualizar(Long usuarioId, Long id, SalvarTransferenciaRequest pedido) {
        exigirContasDiferentes(pedido);
        regras.exigirDataNoLimite(pedido.data());
        regras.travarContas(usuarioId);
        List<Lancamento> lados = lados(usuarioId, encontrar(usuarioId, id));
        lados.forEach(lado -> regras.exigirContaAtiva(usuarioId, lado.getContaId(), "esta transferência"));
        ContaResumo origem = regras.contaParaLancar(usuarioId, pedido.contaOrigemId());
        ContaResumo destino = regras.contaParaLancar(usuarioId, pedido.contaDestinoId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(origem, destino));
        Lancamento saida = lados.stream().filter(lado -> lado.getValor().signum() < 0).findFirst().orElseThrow();
        Lancamento entrada = lados.stream().filter(lado -> lado.getValor().signum() > 0).findFirst().orElseThrow();
        if (!YearMonth.from(pedido.data()).equals(YearMonth.from(saida.getData()))) {
            regras.exigirLimiteDoMes(usuarioId, pedido.data(), 2);
        }
        aplicar(saida, entrada, pedido, regras.agora());
        return lista.itemDaTransferencia(usuarioId, lados);
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        regras.travarContas(usuarioId);
        Transferencia transferencia = encontrar(usuarioId, id);
        List<Lancamento> lados = lados(usuarioId, transferencia);
        lados.forEach(lado -> regras.exigirContaAtiva(usuarioId, lado.getContaId(), "esta transferência"));
        // Primeiro os lados, depois o vínculo: o Hibernate apaga na ordem pedida.
        lancamentos.deleteAll(lados);
        transferencias.delete(transferencia);
    }

    private Transferencia encontrar(Long usuarioId, Long id) {
        return transferencias.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transferência não encontrada."));
    }

    private List<Lancamento> lados(Long usuarioId, Transferencia transferencia) {
        return lancamentos.findAllByUsuarioIdAndTransferenciaId(usuarioId, transferencia.getId());
    }

    private static void exigirContasDiferentes(SalvarTransferenciaRequest pedido) {
        if (pedido.contaOrigemId().equals(pedido.contaDestinoId())) {
            throw new CampoInvalidoException("contaDestinoId", "Escolha uma conta diferente da de origem.");
        }
    }

    /** Mesma data, mesmo valor e mesma descrição nos dois lados; saída negativa, entrada positiva. */
    private static void aplicar(Lancamento saida, Lancamento entrada, SalvarTransferenciaRequest pedido, Instant agora) {
        String descricao = pedido.descricao() == null || pedido.descricao().isEmpty() ? DESCRICAO_PADRAO : pedido.descricao();
        saida.alterar(TipoLancamento.TRANSFERENCIA, pedido.contaOrigemId(), pedido.data(), descricao, pedido.valor().negate(),
                null, pedido.observacao(), agora);
        entrada.alterar(TipoLancamento.TRANSFERENCIA, pedido.contaDestinoId(), pedido.data(), descricao, pedido.valor(),
                null, pedido.observacao(), agora);
    }
}
