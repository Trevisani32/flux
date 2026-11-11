package com.flux.categorizacao.interno;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.categorizacao.interno.dto.AplicacaoResponse;
import com.flux.categorizacao.interno.dto.AplicadosResponse;
import com.flux.categorizacao.interno.dto.AplicarRequest;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.LancamentosParaRegras;
import com.flux.lancamentos.LancamentosParaRegras.Candidato;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Aplicar regras" (decisões 16 a 18): as receitas e despesas sem categoria, manuais e importadas, de
 * contas ativas e de qualquer data, cada uma com a primeira regra ativa que casa. A prévia não grava; a
 * aplicação refaz a conta dentro das travas e nunca troca uma categoria. Uma regra que não é do usuário
 * (inexistente, excluída ou de outro) dá 422: no corpo do "Aplicar", pelo ADR 0007; na {@code ?regra=}
 * da prévia, por decisão desta fatia (ADR 0015).
 */
@Service
public class AplicacaoDeRegras {

    private static final int EXEMPLOS = 3;

    private final RegraRepository repositorio;
    private final ContasConsulta contas;
    private final CategoriasConsulta categorias;
    private final LancamentosParaRegras lancamentos;

    public AplicacaoDeRegras(RegraRepository repositorio, ContasConsulta contas, CategoriasConsulta categorias,
                             LancamentosParaRegras lancamentos) {
        this.repositorio = repositorio;
        this.contas = contas;
        this.categorias = categorias;
        this.lancamentos = lancamentos;
    }

    /** Agrupada pela regra, na ordem da posição; com {@code regraId}, só o grupo dela (decisão 19). */
    @Transactional(readOnly = true)
    public AplicacaoResponse previa(Long usuarioId, Long regraId) {
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        if (regraId != null) {
            conferir(regras, List.of(regraId));
        }
        Calculo calculo = calcular(usuarioId, regras);
        Map<Regra, List<Candidato>> porRegra = new LinkedHashMap<>();
        calculo.casados().stream()
                .filter(casado -> regraId == null || casado.regra().getId().equals(regraId))
                .forEach(casado -> porRegra.computeIfAbsent(casado.regra(), regra -> new ArrayList<>()).add(casado.candidato()));
        List<AplicacaoResponse.Grupo> grupos = porRegra.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((uma, outra) -> Integer.compare(uma.getPosicao(), outra.getPosicao())))
                .map(entrada -> grupo(entrada.getKey(), entrada.getValue(), calculo))
                .toList();
        return new AplicacaoResponse(grupos.stream().mapToInt(AplicacaoResponse.Grupo::quantidade).sum(), grupos);
    }

    @Transactional
    public AplicadosResponse aplicar(Long usuarioId, AplicarRequest pedido) {
        contas.travar(usuarioId);
        categorias.travar(usuarioId);
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        conferir(regras, pedido.regras());
        Set<Long> escolhidas = new HashSet<>(pedido.regras());
        List<LancamentosParaRegras.Categorizacao> categorizacoes = calcular(usuarioId, regras).casados().stream()
                .filter(casado -> escolhidas.contains(casado.regra().getId()))
                .map(casado -> new LancamentosParaRegras.Categorizacao(casado.candidato().id(), casado.regra().getCategoriaId(),
                        casado.regra().getDescricao()))
                .toList();
        return new AplicadosResponse(categorizacoes.isEmpty() ? 0 : lancamentos.categorizar(usuarioId, categorizacoes));
    }

    /** Toda regra pedida precisa ser do usuário (a pausada é dele e só não casa). */
    private static void conferir(List<Regra> regras, List<Long> pedidas) {
        Set<Long> doUsuario = regras.stream().map(Regra::getId).collect(Collectors.toSet());
        if (!doUsuario.containsAll(pedidas)) {
            throw new RegraDeNegocioException("Regra não encontrada.", "REGRA_INVALIDA");
        }
    }

    /** Cada lançamento sem categoria de conta ativa com a primeira regra ativa que casa, se houver. */
    private Calculo calcular(Long usuarioId, List<Regra> regras) {
        Map<Long, CategoriaResumo> doUsuario = categorias.listar(usuarioId).stream()
                .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        Map<Long, ContaResumo> contasDoUsuario = contas.listar(usuarioId).stream()
                .collect(Collectors.toMap(ContaResumo::id, Function.identity()));
        RegrasAtivas ativas = new RegrasAtivas(regras, doUsuario, contasDoUsuario);
        List<Casado> casados = new ArrayList<>();
        if (!ativas.vazia()) {
            for (Candidato candidato : lancamentos.receitasEDespesas(usuarioId)) {
                ContaResumo conta = contasDoUsuario.get(candidato.contaId());
                if (candidato.categoriaId() != null || conta == null || conta.arquivada()) {
                    continue;
                }
                Regra regra = ativas.primeira(candidato.contaId(), RegrasAtivas.natureza(candidato.valor()),
                        Comparacao.chave(candidato.textoParaRegras()));
                if (regra != null) {
                    casados.add(new Casado(candidato, regra));
                }
            }
        }
        return new Calculo(casados, doUsuario, contasDoUsuario);
    }

    /** Os candidatos já vêm do mais recente para o mais antigo: os exemplos são os primeiros. */
    private static AplicacaoResponse.Grupo grupo(Regra regra, List<Candidato> candidatos, Calculo calculo) {
        CategoriaResumo categoria = calculo.categorias().get(regra.getCategoriaId());
        int manuais = (int) candidatos.stream().filter(candidato -> !candidato.importado()).count();
        int renomeados = regra.getDescricao() == null ? 0 : (int) candidatos.stream()
                .filter(candidato -> candidato.importado() && candidato.descricao().equals(candidato.descricaoBanco()))
                .count();
        List<AplicacaoResponse.Exemplo> exemplos = candidatos.stream().limit(EXEMPLOS)
                .map(candidato -> new AplicacaoResponse.Exemplo(candidato.data(), candidato.descricao(),
                        calculo.contas().get(candidato.contaId()).nome(), candidato.valor()))
                .toList();
        return new AplicacaoResponse.Grupo(regra.getId(), regra.getPosicao(), regra.getCondicao().name(), regra.getTexto(),
                categoria.id(), categoria.nome(), categoria.nomeDaMae(), regra.getDescricao(), candidatos.size(), manuais, renomeados,
                exemplos);
    }

    private record Casado(Candidato candidato, Regra regra) {
    }

    private record Calculo(List<Casado> casados, Map<Long, CategoriaResumo> categorias, Map<Long, ContaResumo> contas) {
    }
}
