package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.contas.ContaResumo;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.RestaurarRequest;
import com.flux.lancamentos.interno.dto.SalvarLancamentoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receitas e despesas, manuais e importadas. Um lado de transferência não é editado por aqui.
 * No importado só mudam descrição, categoria e observação, e excluir o ignora (decisões 29 e 30).
 */
@Service
public class LancamentoService {

    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LancamentoRepository repositorio;
    private final RegrasDosLancamentos regras;
    private final ListaDosLancamentos lista;
    private final CategoriasConsulta categorias;

    public LancamentoService(LancamentoRepository repositorio, RegrasDosLancamentos regras, ListaDosLancamentos lista,
                             CategoriasConsulta categorias) {
        this.repositorio = repositorio;
        this.regras = regras;
        this.lista = lista;
        this.categorias = categorias;
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
        if (lancamento.importado()) {
            exigirOsCamposDoExtrato(lancamento, pedido);
        } else {
            ContaResumo conta = regras.contaParaLancar(usuarioId, pedido.contaId());
            regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(conta));
        }
        if (pedido.categoriaId() != null) {
            regras.exigirCategoria(usuarioId, pedido.categoriaId(), pedido.tipo(), lancamento.getCategoriaId());
        }
        if (lancamento.importado()) {
            lancamento.alterarImportado(pedido.descricao(), pedido.categoriaId(), pedido.observacao(), regras.agora());
            return lista.itemDoLancamento(usuarioId, lancamento);
        }
        if (!YearMonth.from(pedido.data()).equals(YearMonth.from(lancamento.getData()))) {
            regras.exigirLimiteDoMes(usuarioId, pedido.data(), 1);
        }
        aplicar(lancamento, pedido);
        return lista.itemDoLancamento(usuarioId, lancamento);
    }

    /** O manual sai de vez; o importado é ignorado e perde a categoria (decisão 30). */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        regras.travarContasECategorias(usuarioId);
        Lancamento lancamento = encontrar(usuarioId, id);
        regras.exigirContaAtiva(usuarioId, lancamento.getContaId(), "este lançamento");
        if (lancamento.importado()) {
            lancamento.ignorar(regras.agora());
        } else {
            repositorio.delete(lancamento);
        }
    }

    /**
     * Volta um importado ignorado para a lista (decisão 31). A categoria pedida só vale se ainda
     * servir (do usuário, da natureza e ativa); senão, ele volta sem categoria.
     */
    @Transactional
    public ItemDaLista restaurar(Long usuarioId, Long id, RestaurarRequest pedido) {
        regras.travarContasECategorias(usuarioId);
        Lancamento lancamento = repositorio.findByIdAndUsuarioIdAndIgnoradoEmIsNotNull(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento não encontrado."));
        ContaResumo conta = regras.exigirContaAtivaPara(usuarioId, lancamento.getContaId(), "restaurar este lançamento");
        if (lancamento.getData().isBefore(conta.dataSaldoInicial())) {
            throw new RegraDeNegocioException("Não dá para restaurar: a data, " + lancamento.getData().format(DATA_CURTA)
                    + ", é anterior ao saldo inicial de " + conta.nome() + " (" + conta.dataSaldoInicial().format(DATA_CURTA)
                    + ").", "DATA_ANTES_DO_SALDO_INICIAL");
        }
        Long categoriaId = pedido == null || pedido.categoriaId() == null ? null
                : categoriaQueServe(usuarioId, pedido.categoriaId(), lancamento.getTipo()).map(CategoriaResumo::id).orElse(null);
        lancamento.restaurar(categoriaId);
        return lista.itemDoLancamento(usuarioId, lancamento);
    }

    private Optional<CategoriaResumo> categoriaQueServe(Long usuarioId, Long categoriaId, TipoLancamento tipo) {
        return categorias.buscar(usuarioId, categoriaId)
                .filter(categoria -> categoria.natureza().equals(tipo.name()) && !categoria.arquivada());
    }

    private Lancamento encontrar(Long usuarioId, Long id) {
        return repositorio.findByIdAndUsuarioIdAndTransferenciaIdIsNullAndIgnoradoEmIsNull(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento não encontrado."));
    }

    /** A transferência tem endpoint próprio (/api/transferencias). */
    private static void exigirReceitaOuDespesa(SalvarLancamentoRequest pedido) {
        if (pedido.tipo() == TipoLancamento.TRANSFERENCIA) {
            throw new CampoInvalidoException("tipo", "Escolha receita ou despesa.");
        }
    }

    /** Tipo, valor, data e conta do importado vêm do extrato (decisão 29). */
    private static void exigirOsCamposDoExtrato(Lancamento lancamento, SalvarLancamentoRequest pedido) {
        BigDecimal valor = pedido.tipo() == TipoLancamento.DESPESA ? pedido.valor().negate() : pedido.valor();
        if (pedido.tipo() != lancamento.getTipo() || valor.compareTo(lancamento.getValor()) != 0
                || !pedido.data().equals(lancamento.getData()) || !pedido.contaId().equals(lancamento.getContaId())) {
            throw new RegraDeNegocioException("Tipo, valor, data e conta de um lançamento importado não mudam.",
                    "IMPORTADO_TRAVADO");
        }
    }

    /** O valor chega positivo; a despesa é gravada negativa (ADR 0002). */
    private void aplicar(Lancamento lancamento, SalvarLancamentoRequest pedido) {
        BigDecimal valor = pedido.tipo() == TipoLancamento.DESPESA ? pedido.valor().negate() : pedido.valor();
        lancamento.alterar(pedido.tipo(), pedido.contaId(), pedido.data(), pedido.descricao(), valor, pedido.categoriaId(),
                pedido.observacao(), regras.agora());
    }
}
