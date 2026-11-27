package com.flux.recorrencias;

import java.time.LocalDate;
import java.util.List;

/**
 * Leitura das ocorrências das recorrências para outros módulos (a projeção de saldo, desde a Fatia 07b, ADR 0023). As mesmas
 * que Recorrências, Lançamentos e o Início mostram (ADR 0022): calculadas a cada chamada, nunca gravadas.
 */
public interface RecorrenciasConsulta {

    /**
     * As ocorrências não tratadas com a data de {@code de} a {@code ate}, inclusive, das recorrências que não estão pausadas,
     * presas à janela de hoje − 60 dias a hoje + 12 meses: as atrasadas e as previstas. Sem ordem definida. Faz um número
     * fixo de consultas por chamada (os lançamentos ligados às tratadas, em lotes de até 1.000 ids), quantas forem as
     * recorrências e as tratadas.
     */
    List<OcorrenciaPrevista> previstas(Long usuarioId, LocalDate de, LocalDate ate);
}
