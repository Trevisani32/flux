package com.flux.projecao.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CalculoDaProjecao {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private CalculoDaProjecao() {
    }

    record Entrada<T>(Long contaId, LocalDate dia, BigDecimal valor, boolean naLista, T item) {
    }

    record Trecho(LocalDate de, LocalDate ate) {
    }

    record Resumo(BigDecimal hoje, BigDecimal pendencias, BigDecimal inicio, BigDecimal menor, LocalDate diaDoMenor,
                  BigDecimal fim, int diasNegativos, List<Trecho> trechos) {
    }

    record Dia<T>(LocalDate data, BigDecimal movimento, BigDecimal saldo, List<T> itens) {
    }

    record Resultado<T>(Resumo total, Map<Long, Resumo> porConta, List<T> pendencias, List<Dia<T>> dias) {
    }

    static <T> Resultado<T> calcular(LocalDate hoje, LocalDate ate, Map<Long, BigDecimal> saldosDeHoje,
                                     List<Entrada<T>> entradas) {
        validar(hoje, ate, saldosDeHoje, entradas);
        Map<Long, Estado> porConta = new LinkedHashMap<>();
        saldosDeHoje.forEach((contaId, saldo) -> porConta.put(contaId, new Estado(saldo)));
        Estado total = new Estado(saldosDeHoje.values().stream().reduce(ZERO, BigDecimal::add));
        Map<LocalDate, List<Entrada<T>>> porDia = new LinkedHashMap<>();
        for (Entrada<T> entrada : entradas) {
            porDia.computeIfAbsent(entrada.dia(), ignorado -> new ArrayList<>()).add(entrada);
        }
        List<T> pendencias = new ArrayList<>();
        List<Dia<T>> dias = new ArrayList<>();
        for (LocalDate data = hoje; !data.isAfter(ate); data = data.plusDays(1)) {
            List<Entrada<T>> doDia = porDia.getOrDefault(data, List.of());
            for (Entrada<T> entrada : doDia) {
                porConta.get(entrada.contaId()).somar(entrada.valor());
                total.somar(entrada.valor());
            }
            for (Estado estado : porConta.values()) {
                estado.visitar(data);
            }
            total.visitar(data);
            if (data.equals(hoje)) {
                doDia.stream().filter(Entrada::naLista).map(Entrada::item).forEach(pendencias::add);
            } else {
                List<Entrada<T>> visiveis = doDia.stream().filter(Entrada::naLista).toList();
                if (!visiveis.isEmpty()) {
                    BigDecimal movimento = visiveis.stream().map(Entrada::valor).reduce(ZERO, BigDecimal::add);
                    dias.add(new Dia<>(data, escala(movimento), total.saldo(), visiveis.stream().map(Entrada::item).toList()));
                }
            }
        }
        Map<Long, Resumo> resumos = new LinkedHashMap<>();
        porConta.forEach((contaId, estado) -> resumos.put(contaId, estado.resumo()));
        return new Resultado<>(total.resumo(), Collections.unmodifiableMap(resumos), List.copyOf(pendencias), List.copyOf(dias));
    }

    private static <T> void validar(LocalDate hoje, LocalDate ate, Map<Long, BigDecimal> saldosDeHoje,
                                    List<Entrada<T>> entradas) {
        for (Entrada<T> entrada : entradas) {
            if (!saldosDeHoje.containsKey(entrada.contaId()) || entrada.dia().isBefore(hoje) || entrada.dia().isAfter(ate)) {
                throw new IllegalArgumentException();
            }
        }
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2);
    }

    private static final class Estado {

        private final BigDecimal hoje;
        private BigDecimal pendencias = ZERO;
        private BigDecimal saldo;
        private BigDecimal inicio;
        private BigDecimal menor;
        private LocalDate diaDoMenor;
        private LocalDate ultimoDia;
        private int diasNegativos;
        private LocalDate inicioDoTrecho;
        private final List<Trecho> trechos = new ArrayList<>();

        private Estado(BigDecimal saldo) {
            this.hoje = escala(saldo);
            this.saldo = this.hoje;
        }

        private void somar(BigDecimal valor) {
            saldo = escala(saldo.add(valor));
        }

        private BigDecimal saldo() {
            return saldo;
        }

        private void visitar(LocalDate data) {
            ultimoDia = data;
            if (inicio == null) {
                pendencias = escala(saldo.subtract(hoje));
                inicio = saldo;
            }
            if (menor == null || saldo.compareTo(menor) < 0) {
                menor = saldo;
                diaDoMenor = data;
            }
            if (saldo.signum() < 0) {
                diasNegativos++;
                if (inicioDoTrecho == null) {
                    inicioDoTrecho = data;
                }
            } else if (inicioDoTrecho != null) {
                trechos.add(new Trecho(inicioDoTrecho, data.minusDays(1)));
                inicioDoTrecho = null;
            }
        }

        private Resumo resumo() {
            if (inicioDoTrecho != null) {
                trechos.add(new Trecho(inicioDoTrecho, ultimoDia));
                inicioDoTrecho = null;
            }
            return new Resumo(hoje, pendencias, inicio, menor, diaDoMenor, saldo, diasNegativos, List.copyOf(trechos));
        }
    }
}
