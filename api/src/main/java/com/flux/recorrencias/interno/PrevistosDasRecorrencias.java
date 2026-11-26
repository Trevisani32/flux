package com.flux.recorrencias.interno;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.flux.lancamentos.PrevistosNaLista;
import com.flux.recorrencias.interno.dto.OcorrenciaResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrevistosDasRecorrencias implements PrevistosNaLista {

    private final RecorrenciaService recorrencias;

    public PrevistosDasRecorrencias(RecorrenciaService recorrencias) {
        this.recorrencias = recorrencias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Previsto> previstos(Long usuarioId, LocalDate de, LocalDate ate) {
        return recorrencias.ocorrenciasVisiveis(usuarioId, de, ate).stream()
                .map(this::paraPrevisto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Ligacoes ligacoes(Long usuarioId, Collection<Long> lancamentoIds, Collection<Long> compraIds) {
        Map<Long, Long> porLancamento = new HashMap<>();
        Map<Long, Long> porCompra = new HashMap<>();
        if (!lancamentoIds.isEmpty()) {
            // Só a efetivada ainda válida marca o lançamento; importado ignorado volta a ser previsto.
            recorrencias.ligacoesPorLancamento(usuarioId, lancamentoIds)
                    .forEach((id, recorrenciaId) -> porLancamento.put(id, recorrenciaId));
        }
        if (!compraIds.isEmpty()) {
            recorrencias.ligacoesPorCompra(usuarioId, compraIds).forEach((id, recorrenciaId) -> porCompra.put(id, recorrenciaId));
        }
        return new Ligacoes(Map.copyOf(porLancamento), Map.copyOf(porCompra));
    }

    private Previsto paraPrevisto(OcorrenciaResponse item) {
        return new Previsto(item.recorrenciaId(), item.periodo(), item.data(), item.descricao(), item.valor(), item.tipo(),
                item.valorVaria(), item.contaId(), item.cartaoId(), item.categoriaId(), item.atrasada());
    }
}
