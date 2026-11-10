package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.contas.ContaResumo;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.SalvarLancamentoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receitas e despesas manuais. Um lado de transferência não é editado por aqui. */
@Service
public class LancamentoService {

    private final LancamentoRepository repositorio;
    private final RegrasDosLancamentos regras;
    private final ListaDosLancamentos lista;

    public LancamentoService(LancamentoRepository repositorio, RegrasDosLancamentos regras, ListaDosLancamentos lista) {
        this.repositorio = repositorio;
        this.regras = regras;
        this.lista = lista;
    }

    @Transactional(readOnly = true)
    public ItemDaLista buscar(Long usuarioId, Long id) {
        return lista.itemDoLancamento(usuarioId, encontrar(usuarioId, id));
    }

    @Transactional
    public ItemDaLista criar(Long usuarioId, SalvarLancamentoRequest pedido) {
        exigirReceitaOuDespesa(pedido);
        regras.exigirDataNoLimite(pedido.data());
        regras.travarContasECategorias(usuarioId);
        ContaResumo conta = regras.contaParaLancar(usuarioId, pedido.contaId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(conta));
        if (pedido.categoriaId() != null) {
            regras.exigirCategoria(usuarioId, pedido.categoriaId(), pedido.tipo(), null);
        }
        regras.exigirLimiteTotal(usuarioId, 1);
        regras.exigirLimiteDoMes(usuarioId, pedido.data(), 1);
        Lancamento lancamento = new Lancamento(usuarioId, null, regras.agora());
        aplicar(lancamento, pedido);
        repositorio.save(lancamento);
        return lista.itemDoLancamento(usuarioId, lancamento);
    }

    @Transactional
    public ItemDaLista atualizar(Long usuarioId, Long id, SalvarLancamentoRequest pedido) {
        exigirReceitaOuDespesa(pedido);
        regras.exigirDataNoLimite(pedido.data());
        regras.travarContasECategorias(usuarioId);
        Lancamento lancamento = encontrar(usuarioId, id);
        regras.exigirContaAtiva(usuarioId, lancamento.getContaId(), "este lançamento");
        ContaResumo conta = regras.contaParaLancar(usuarioId, pedido.contaId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(conta));
        if (pedido.categoriaId() != null) {
            regras.exigirCategoria(usuarioId, pedido.categoriaId(), pedido.tipo(), lancamento.getCategoriaId());
        }
        if (!YearMonth.from(pedido.data()).equals(YearMonth.from(lancamento.getData()))) {
            regras.exigirLimiteDoMes(usuarioId, pedido.data(), 1);
        }
        aplicar(lancamento, pedido);
        return lista.itemDoLancamento(usuarioId, lancamento);
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        regras.travarContasECategorias(usuarioId);
        Lancamento lancamento = encontrar(usuarioId, id);
        regras.exigirContaAtiva(usuarioId, lancamento.getContaId(), "este lançamento");
        repositorio.delete(lancamento);
    }

    private Lancamento encontrar(Long usuarioId, Long id) {
        return repositorio.findByIdAndUsuarioIdAndTransferenciaIdIsNull(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento não encontrado."));
    }

    /** A transferência tem endpoint próprio (/api/transferencias). */
    private static void exigirReceitaOuDespesa(SalvarLancamentoRequest pedido) {
        if (pedido.tipo() == TipoLancamento.TRANSFERENCIA) {
            throw new CampoInvalidoException("tipo", "Escolha receita ou despesa.");
        }
    }

    /** O valor chega positivo; a despesa é gravada negativa (ADR 0002). */
    private void aplicar(Lancamento lancamento, SalvarLancamentoRequest pedido) {
        BigDecimal valor = pedido.tipo() == TipoLancamento.DESPESA ? pedido.valor().negate() : pedido.valor();
        lancamento.alterar(pedido.tipo(), pedido.contaId(), pedido.data(), pedido.descricao(), valor, pedido.categoriaId(),
                pedido.observacao(), regras.agora());
    }
}
