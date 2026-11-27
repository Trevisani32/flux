package com.flux.projecao.interno;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;

final class PedidoDaProjecao {

    private PedidoDaProjecao() {
    }

    record Periodo(LocalDate hoje, LocalDate ate, LocalDate padrao, LocalDate tresMeses, LocalDate seisMeses,
                   LocalDate dozeMeses) {
    }

    static Periodo periodo(LocalDate hoje, String ate) {
        LocalDate padrao = YearMonth.from(hoje).plusMonths(1).atEndOfMonth();
        LocalDate tresMeses = hoje.plusMonths(3);
        LocalDate seisMeses = hoje.plusMonths(6);
        LocalDate dozeMeses = hoje.plusMonths(12);
        if (ate == null || ate.isBlank()) {
            return new Periodo(hoje, padrao, padrao, tresMeses, seisMeses, dozeMeses);
        }
        LocalDate fim;
        try {
            fim = LocalDate.parse(ate);
        } catch (RuntimeException ex) {
            throw new CampoInvalidoException("ate", "Escolha uma data válida.");
        }
        if (!fim.isAfter(hoje)) {
            throw new CampoInvalidoException("ate", "Escolha uma data depois de hoje.");
        }
        if (fim.isAfter(dozeMeses)) {
            throw new CampoInvalidoException("ate", "A projeção vai até " + textoDaData(dozeMeses) + ".");
        }
        return new Periodo(hoje, fim, padrao, tresMeses, seisMeses, dozeMeses);
    }

    static List<Long> escolhidas(String contas, List<ContaResumo> ativasEmOrdem) {
        List<ContaResumo> ativas = ativasEmOrdem.stream().filter(conta -> !conta.arquivada())
                .sorted(Comparator.comparing(ContaResumo::nome, TextoComparavel.ordemAlfabetica()).thenComparing(ContaResumo::id))
                .toList();
        if (contas == null) {
            return ativas.stream().filter(conta -> !"INVESTIMENTO".equals(conta.tipo())).map(ContaResumo::id).toList();
        }
        Set<Long> pedidas = new HashSet<>();
        for (String parte : contas.split(",", -1)) {
            try {
                pedidas.add(Long.parseLong(parte.trim()));
            } catch (NumberFormatException ignored) {
                // Id inválido sai calado da seleção.
            }
        }
        List<Long> resultado = new ArrayList<>();
        for (ContaResumo conta : ativas) {
            if (pedidas.contains(conta.id())) {
                resultado.add(conta.id());
            }
        }
        return List.copyOf(resultado);
    }

    private static String textoDaData(LocalDate data) {
        return "%02d/%02d/%04d".formatted(data.getDayOfMonth(), data.getMonthValue(), data.getYear());
    }
}
