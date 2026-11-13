package com.flux.lancamentos.interno;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.LancamentosParaRegras;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A API das regras de categorização sobre a tabela de lançamentos (ADR 0015). */
@Component
public class LancamentosNasRegras implements LancamentosParaRegras {

    /**
     * Lotes do "in" ao buscar os lançamentos a categorizar. Cada lote vai para o banco e sai do contexto
     * de persistência antes do próximo: no limite (100.000 lançamentos), a transação não segura todos na
     * memória.
     */
    private static final int LOTE = 1_000;

    private final LancamentoRepository repositorio;
    private final RegrasDosLancamentos regras;
    private final ContasConsulta contas;
    private final CategoriasConsulta categorias;
    private final EntityManager entityManager;

    public LancamentosNasRegras(LancamentoRepository repositorio, RegrasDosLancamentos regras, ContasConsulta contas,
                                CategoriasConsulta categorias, EntityManager entityManager) {
        this.repositorio = repositorio;
        this.regras = regras;
        this.contas = contas;
        this.categorias = categorias;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Candidato> receitasEDespesas(Long usuarioId) {
        return repositorio.paraAsRegras(usuarioId, TipoLancamento.TRANSFERENCIA).stream()
                .map(linha -> new Candidato(linha.id(), linha.contaId(), linha.data(), linha.valor(), linha.descricao(),
                        linha.descricaoBanco(), linha.categoriaId()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int categorizar(Long usuarioId, List<Categorizacao> categorizacoes) {
        regras.travarContasECategorias(usuarioId);
        Map<Long, CategoriaResumo> doUsuario = categorias.listar(usuarioId).stream()
                .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        Set<Long> contasAtivas = contas.listar(usuarioId).stream().filter(conta -> !conta.arquivada()).map(ContaResumo::id)
                .collect(Collectors.toSet());
        Map<Long, Categorizacao> porLancamento = categorizacoes.stream()
                .collect(Collectors.toMap(Categorizacao::lancamentoId, Function.identity(), (primeira, outra) -> primeira));
        List<Long> ids = new ArrayList<>(porLancamento.keySet());
        Instant agora = regras.agora();
        int mudaram = 0;
        for (int inicio = 0; inicio < ids.size(); inicio += LOTE) {
            List<Long> idsDoLote = ids.subList(inicio, Math.min(inicio + LOTE, ids.size()));
            List<Lancamento> lote = repositorio.findAllByUsuarioIdAndIdIn(usuarioId, idsDoLote);
            for (Lancamento lancamento : lote) {
                Categorizacao categorizacao = porLancamento.get(lancamento.getId());
                CategoriaResumo categoria = doUsuario.get(categorizacao.categoriaId());
                if (serve(lancamento, categoria, contasAtivas)) {
                    lancamento.categorizarPorRegra(categoria.id(), categorizacao.descricao(), agora);
                    mudaram++;
                }
            }
            // Só os lançamentos deste lote saem do contexto: o que o chamador carregou continua com ele.
            entityManager.flush();
            lote.forEach(entityManager::detach);
        }
        return mudaram;
    }

    /**
     * Receita ou despesa de conta (a de fatura só entra na 05c, decisão 27 da 05a) ainda sem categoria, não excluída,
     * em conta ativa, e a categoria ativa e da natureza.
     */
    private static boolean serve(Lancamento lancamento, CategoriaResumo categoria, Set<Long> contasAtivas) {
        return !lancamento.transferencia() && !lancamento.deFatura() && lancamento.getIgnoradoEm() == null && lancamento.getCategoriaId() == null
                && contasAtivas.contains(lancamento.getContaId()) && categoria != null && !categoria.arquivada()
                && categoria.natureza().equals(lancamento.getTipo().name());
    }
}
