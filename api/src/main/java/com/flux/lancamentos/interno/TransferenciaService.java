package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.contas.ContaResumo;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.SalvarTransferenciaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transferências (ADR 0004 e ADR 0013): cada uma é um vínculo e dois lançamentos, a saída
 * na origem e a entrada no destino, sempre gravados, editados e excluídos juntos. A convertida
 * de importados (Fatia 04c, ADR 0016) só muda a descrição e a observação e não se exclui: ela se
 * desfaz, pelo {@link ConversaoService}.
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

    /**
     * Na manual, muda tudo nos dois lados. Na convertida (decisão 31 da 04c), o pedido repete o valor,
     * as contas e a data da saída, e só a descrição e a observação mudam, nos dois lados; qualquer
     * outra mudança é o 422 IMPORTADO_TRAVADO. Com uma conta arquivada, a convertida diz "editar ou
     * desfazer" (ela não se exclui), e a manual continua com "editar ou excluir".
     */
    @Transactional
    public ItemDaLista atualizar(Long usuarioId, Long id, SalvarTransferenciaRequest pedido) {
        exigirContasDiferentes(pedido);
        regras.exigirDataNoLimite(pedido.data());
        regras.travarContas(usuarioId);
        List<Lancamento> lados = lados(usuarioId, encontrar(usuarioId, id));
        boolean convertida = ConversaoService.convertida(lados);
        String acao = convertida ? "editar ou desfazer esta transferência" : "editar ou excluir esta transferência";
        lados.forEach(lado -> regras.exigirContaAtivaPara(usuarioId, lado.getContaId(), acao));
        Lancamento saida = lados.stream().filter(lado -> lado.getValor().signum() < 0).findFirst().orElseThrow();
        Lancamento entrada = lados.stream().filter(lado -> lado.getValor().signum() > 0).findFirst().orElseThrow();
        if (convertida) {
            exigirOsCamposDaConvertida(saida, entrada, pedido);
            String descricao = descricaoDoPedido(pedido);
            Instant agora = regras.agora();
            saida.alterarLadoConvertido(descricao, pedido.observacao(), agora);
            entrada.alterarLadoConvertido(descricao, pedido.observacao(), agora);
            return lista.itemDaTransferencia(usuarioId, lados);
        }
        ContaResumo origem = regras.contaParaLancar(usuarioId, pedido.contaOrigemId());
        ContaResumo destino = regras.contaParaLancar(usuarioId, pedido.contaDestinoId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(origem, destino));
        if (!YearMonth.from(pedido.data()).equals(YearMonth.from(saida.getData()))) {
            regras.exigirLimiteDoMes(usuarioId, pedido.data(), 2);
        }
        aplicar(saida, entrada, pedido, regras.agora());
        return lista.itemDaTransferencia(usuarioId, lados);
    }

    /**
     * A convertida não se exclui (decisão 33 da 04c): apagar os lados levaria as chaves de importação. A
     * recusa vem antes da conta arquivada, que mandaria reativar a conta para uma exclusão que não existe.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        regras.travarContas(usuarioId);
        Transferencia transferencia = encontrar(usuarioId, id);
        List<Lancamento> lados = lados(usuarioId, transferencia);
        if (ConversaoService.convertida(lados)) {
            throw new RegraDeNegocioException("Uma transferência convertida não se exclui. Desfaça a transferência e exclua cada lançamento.",
                    "TRANSFERENCIA_CONVERTIDA");
        }
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

    /** Decisão 31 da 04c: valor, contas e a data da saída de uma convertida não mudam. */
    private static void exigirOsCamposDaConvertida(Lancamento saida, Lancamento entrada, SalvarTransferenciaRequest pedido) {
        if (pedido.valor().compareTo(entrada.getValor()) != 0 || !pedido.data().equals(saida.getData())
                || !pedido.contaOrigemId().equals(saida.getContaId()) || !pedido.contaDestinoId().equals(entrada.getContaId())) {
            throw new RegraDeNegocioException("Contas, datas e valor de uma transferência convertida não mudam.", "IMPORTADO_TRAVADO");
        }
    }

    private static String descricaoDoPedido(SalvarTransferenciaRequest pedido) {
        return pedido.descricao() == null || pedido.descricao().isEmpty() ? DESCRICAO_PADRAO : pedido.descricao();
    }

    /** Mesma data, mesmo valor e mesma descrição nos dois lados; saída negativa, entrada positiva. */
    private static void aplicar(Lancamento saida, Lancamento entrada, SalvarTransferenciaRequest pedido, Instant agora) {
        String descricao = descricaoDoPedido(pedido);
        saida.alterar(TipoLancamento.TRANSFERENCIA, pedido.contaOrigemId(), pedido.data(), descricao, pedido.valor().negate(),
                null, pedido.observacao(), agora);
        entrada.alterar(TipoLancamento.TRANSFERENCIA, pedido.contaDestinoId(), pedido.data(), descricao, pedido.valor(),
                null, pedido.observacao(), agora);
    }
}