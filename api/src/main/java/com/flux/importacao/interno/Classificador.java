package com.flux.importacao.interno;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.lancamentos.LancamentosImportados.Existente;

/**
 * Classifica cada linha do arquivo, nesta ordem (decisão 14): fica de fora (ilegível, valor
 * zero, linha de saldo, data além de hoje + 12 meses) → já importada → antes do saldo inicial →
 * provável duplicada → nova. Não lê o banco: recebe os lançamentos que já existem.
 */
final class Classificador {

    enum Situacao { NOVA, PROVAVEL, JA_IMPORTADA, ANTES_DO_SALDO_INICIAL, FORA }

    enum Motivo { ILEGIVEL, VALOR_ZERO, LINHA_DE_SALDO, DATA_ALEM_DO_LIMITE }

    /**
     * {@code chave}: a que a linha grava, se for importada. {@code marcada}: como vem na revisão
     * (a nova, menos a futura). {@code parecido}: com quem a provável duplicada se parece.
     */
    record Classificada(ExtratoLido.Linha linha, String chave, Situacao situacao, Motivo motivo, boolean marcada,
                        boolean futuro, Existente parecido, boolean excluida) {

        boolean importavel() {
            return situacao == Situacao.NOVA || situacao == Situacao.PROVAVEL;
        }
    }

    private Classificador() {
    }

    static List<Classificada> classificar(long contaId, List<ExtratoLido.Linha> linhas, List<Existente> existentes,
                                          LocalDate saldoInicial, LocalDate hoje) {
        Map<Integer, Chaves.DaLinha> chaves = Chaves.doArquivo(contaId, linhas);
        Map<String, Existente> porChave = new HashMap<>();
        existentes.stream().filter(existente -> existente.chaveImportacao() != null)
                .forEach(existente -> porChave.put(existente.chaveImportacao(), existente));
        LocalDate limite = hoje.plusMonths(12);
        // Primeiro as chaves: um lançamento já importado não serve de "parecido" para outra linha.
        Map<Integer, String> chaveDaLinha = new HashMap<>();
        Set<Long> pareados = new HashSet<>();
        for (ExtratoLido.Linha linha : linhas) {
            if (motivoDeFora(linha, limite) == null) {
                String chave = chave(chaves.get(linha.numero()), porChave, linha);
                chaveDaLinha.put(linha.numero(), chave);
                if (porChave.containsKey(chave)) {
                    pareados.add(porChave.get(chave).id());
                }
            }
        }
        List<Classificada> classificadas = new ArrayList<>();
        for (ExtratoLido.Linha linha : linhas) {
            Motivo motivo = motivoDeFora(linha, limite);
            if (motivo != null) {
                classificadas.add(new Classificada(linha, null, Situacao.FORA, motivo, false, false, null, false));
                continue;
            }
            String chave = chaveDaLinha.get(linha.numero());
            boolean futuro = linha.data().isAfter(hoje);
            Existente jaImportado = porChave.get(chave);
            if (jaImportado != null) {
                classificadas.add(new Classificada(linha, chave, Situacao.JA_IMPORTADA, null, false, futuro, jaImportado,
                        jaImportado.ignorado()));
            } else if (linha.data().isBefore(saldoInicial)) {
                classificadas.add(new Classificada(linha, chave, Situacao.ANTES_DO_SALDO_INICIAL, null, false, futuro, null, false));
            } else {
                Existente parecido = parecido(linha, existentes, pareados);
                if (parecido != null) {
                    pareados.add(parecido.id());
                    classificadas.add(new Classificada(linha, chave, Situacao.PROVAVEL, null, false, futuro, parecido,
                            parecido.ignorado()));
                } else {
                    classificadas.add(new Classificada(linha, chave, Situacao.NOVA, null, !futuro, futuro, null, false));
                }
            }
        }
        return classificadas;
    }

    /** O que deixa a linha de fora antes de olhar o banco, ou nulo. */
    private static Motivo motivoDeFora(ExtratoLido.Linha linha, LocalDate limite) {
        if (!linha.legivel()) {
            return Motivo.ILEGIVEL;
        }
        if (linha.valor().signum() == 0) {
            return Motivo.VALOR_ZERO;
        }
        if (linhaDeSaldo(linha.descricao())) {
            return Motivo.LINHA_DE_SALDO;
        }
        return linha.data().isAfter(limite) ? Motivo.DATA_ALEM_DO_LIMITE : null;
    }

    /** Decisão 16: a chave do identificador só vale se o lançamento dela tem a mesma data e o mesmo valor. */
    private static String chave(Chaves.DaLinha daLinha, Map<String, Existente> porChave, ExtratoLido.Linha linha) {
        if (daLinha.doIdentificador() == null) {
            return daLinha.doConteudo();
        }
        Existente peloIdentificador = porChave.get(daLinha.doIdentificador());
        return peloIdentificador == null || mesmoMovimento(peloIdentificador, linha) ? daLinha.doIdentificador() : daLinha.doConteudo();
    }

    /** "SALDO", "Saldo anterior", "SALDO DO DIA" (decisão 19). */
    static boolean linhaDeSaldo(String descricao) {
        String chave = TextoComparavel.chave(descricao);
        return chave.equals("saldo") || chave.startsWith("saldo ");
    }

    private static boolean mesmoMovimento(Existente existente, ExtratoLido.Linha linha) {
        return existente.data().equals(linha.data()) && existente.valor().compareTo(linha.valor()) == 0;
    }

    /**
     * Decisão 17: mesma data e mesmo valor, com a mesma descrição (a do banco ou a atual), ou de
     * qualquer descrição se o existente foi lançado à mão. Cada existente casa com uma linha só.
     */
    private static Existente parecido(ExtratoLido.Linha linha, List<Existente> existentes, Set<Long> pareados) {
        String descricao = TextoComparavel.chave(linha.descricao());
        for (Existente existente : existentes) {
            if (pareados.contains(existente.id()) || !mesmoMovimento(existente, linha)) {
                continue;
            }
            boolean mesmaDescricao = descricao.equals(TextoComparavel.chave(existente.descricao()))
                    || (existente.descricaoBanco() != null && descricao.equals(TextoComparavel.chave(existente.descricaoBanco())));
            if (mesmaDescricao || existente.manual()) {
                return existente;
            }
        }
        return null;
    }
}
