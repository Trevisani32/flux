package com.flux.importacao.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.importacao.interno.Classificador.Motivo;
import com.flux.importacao.interno.Classificador.Situacao;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.lancamentos.LancamentosImportados.ExistenteNoCartao;

/**
 * Fatia 05c (decisões 40 a 43): classifica cada linha da fatura, na ordem da importação da conta (decisão 14 da 04a), sem
 * "antes do saldo inicial", que o cartão não tem: fica de fora → já importada → provável duplicada → nova. O valor de cada
 * linha já vem com o sinal do Flux (a compra negativa). Não lê o banco: recebe os lançamentos do cartão que já existem.
 *
 * <ul>
 *   <li>fica de fora: ilegível, valor zero, linha de saldo ou de total, o pagamento da fatura (crédito com "pagamento" na
 *       descrição, decisão 42) e a data fora da faixa da compra (de 10 anos antes a 12 meses depois de hoje). A parcela de
 *       uma compra que já está no Flux a partir de uma parcela seguinte, e a parcela que outra linha do arquivo já cria,
 *       também;</li>
 *   <li>já importada: a chave já existe. Na linha com identificador, a chave dele só vale com a mesma data da compra e o
 *       mesmo valor (a salvaguarda da 04a);</li>
 *   <li>provável duplicada: na linha "Parcela k/N", a parcela k de uma compra em N parcelas na fatura escolhida, com o
 *       mesmo valor; nas outras, a compra à vista (ou o estorno) com a mesma data e o mesmo valor. Lançada à mão, de
 *       qualquer descrição; importada, com a mesma descrição. Cada lançamento casa com uma linha só.</li>
 * </ul>
 *
 * Na linha "Parcela k/N", "o mesmo valor" aceita os centavos da divisão (escolha do dono, ADR 0019): o resto da divisão
 * fica numa parcela só (decisão 15), e as parcelas da mesma compra diferem em até N − 1 centavos. Vale para a já
 * importada, para a compra que já está no Flux e para a provável duplicada; fora disso, é outra compra. A parcela já
 * importada com outros centavos, fora de fatura paga, fica com o valor do banco na confirmação ("Corrigir ao importar",
 * dono): {@link Classificada#valorNoFlux}.
 */
final class ClassificadorDaFatura {

    private ClassificadorDaFatura() {
    }

    /**
     * Uma linha classificada. {@code parcela}: só na linha "Parcela k/N" de compra. {@code chave}: a que a linha grava (na
     * parcela, a base, sem o ":k"). {@code parecido}: na provável, com quem ela se parece; na já importada, o lançamento
     * dela. {@code parcelaQueCobre}: em {@code COMPRA_JA_NO_FLUX}, a primeira parcela da compra no Flux; em
     * {@code PARCELA_DO_ARQUIVO}, a parcela do arquivo que já cria esta.
     */
    record Classificada(ExtratoLido.Linha linha, ParcelaNaDescricao.Parcela parcela, String chave, Situacao situacao, Motivo motivo,
                        boolean marcada, boolean futuro, ExistenteNoCartao parecido, Integer parcelaQueCobre) {

        boolean importavel() {
            return situacao == Situacao.NOVA || situacao == Situacao.PROVAVEL;
        }

        /** A descrição do extrato que a compra grava: sem "Parcela k/N" na parcela. */
        String descricaoDoExtrato() {
            return parcela == null ? linha.descricao() : parcela.descricao();
        }

        /**
         * "Corrigir ao importar" (dono, 2026-11-23): na parcela já importada com outros centavos (dentro dos N − 1 da
         * divisão) e fora de fatura paga, o valor dela no Flux, que a confirmação troca pelo da linha; senão, nulo.
         */
        BigDecimal valorNoFlux() {
            boolean corrige = situacao == Situacao.JA_IMPORTADA && parcela != null && !parecido.faturaPaga()
                    && parecido.valor().compareTo(linha.valor()) != 0 && mesmoValor(parecido.valor(), linha.valor(), parcela.parcelas());
            return corrige ? parecido.valor() : null;
        }
    }

    /** As parcelas reconhecidas, pelo número da linha: só nas compras (valor negativo) legíveis. */
    static Map<Integer, ParcelaNaDescricao.Parcela> parcelas(List<ExtratoLido.Linha> linhas) {
        Map<Integer, ParcelaNaDescricao.Parcela> parcelas = new HashMap<>();
        linhas.stream().filter(ExtratoLido.Linha::legivel).filter(linha -> linha.valor().signum() < 0)
                .forEach(linha -> ParcelaNaDescricao.de(linha.descricaoCompleta())
                        .ifPresent(parcela -> parcelas.put(linha.numero(), parcela)));
        return parcelas;
    }

    /** Até quantos ordinais a base da parcela procura, além do dela. */
    private static final int ORDINAIS = 50;

    static List<Classificada> classificar(long cartaoId, List<ExtratoLido.Linha> linhas, List<ExistenteNoCartao> existentes,
                                          YearMonth fatura, LocalDate hoje) {
        Map<Integer, ParcelaNaDescricao.Parcela> parcelas = parcelas(linhas);
        Map<Integer, Chaves.DaFatura> chaves = Chaves.daFatura(cartaoId, linhas, parcelas);
        Map<String, ExistenteNoCartao> porChave = new HashMap<>();
        Map<String, Integer> primeiraNoFlux = new HashMap<>();
        Map<String, BigDecimal> valorDaBase = new HashMap<>();
        for (ExistenteNoCartao existente : existentes) {
            if (existente.chaveImportacao() != null) {
                porChave.put(existente.chaveImportacao(), existente);
                if (existente.chaveImportacao().startsWith("CPAR:")) {
                    String base = existente.chaveImportacao().substring(0, existente.chaveImportacao().lastIndexOf(':'));
                    primeiraNoFlux.merge(base, existente.numeroParcela(), Math::min);
                    valorDaBase.putIfAbsent(base, existente.valor());
                }
            }
        }
        LocalDate ate = hoje.plusMonths(12);
        LocalDate desde = hoje.minusYears(10);
        // Primeiro as chaves: um lançamento já importado não serve de "parecido" para outra linha.
        Map<Integer, String> chaveDaLinha = new HashMap<>();
        Map<Integer, String> baseDaLinha = new HashMap<>();
        Set<String> tomadas = new HashSet<>();
        Set<Long> pareados = new HashSet<>();
        Map<String, Integer> menorDoArquivo = new HashMap<>();
        for (ExtratoLido.Linha linha : linhas) {
            if (motivoDeFora(linha, ate, desde) != null) {
                continue;
            }
            Chaves.DaFatura daLinha = chaves.get(linha.numero());
            ParcelaNaDescricao.Parcela parcela = parcelas.get(linha.numero());
            String base = parcela == null ? null : base(cartaoId, daLinha, parcela, linha.valor(), valorDaBase, tomadas);
            String chave = parcela == null ? chave(daLinha, porChave, linha) : base + ":" + parcela.numero();
            tomadas.add(chave);
            chaveDaLinha.put(linha.numero(), chave);
            baseDaLinha.put(linha.numero(), base);
            if (porChave.containsKey(chave)) {
                pareados.add(porChave.get(chave).id());
            } else if (base != null) {
                menorDoArquivo.merge(base, parcela.numero(), Math::min);
            }
        }
        List<Classificada> classificadas = new ArrayList<>();
        for (ExtratoLido.Linha linha : linhas) {
            ParcelaNaDescricao.Parcela parcela = parcelas.get(linha.numero());
            Motivo motivo = motivoDeFora(linha, ate, desde);
            if (motivo != null) {
                classificadas.add(new Classificada(linha, parcela, null, Situacao.FORA, motivo, false, false, null, null));
                continue;
            }
            String chave = chaveDaLinha.get(linha.numero());
            String base = baseDaLinha.get(linha.numero());
            boolean futuro = linha.data().isAfter(hoje);
            ExistenteNoCartao jaImportado = porChave.get(chave);
            if (jaImportado != null) {
                classificadas.add(new Classificada(linha, parcela, base == null ? chave : base, Situacao.JA_IMPORTADA, null, false, futuro,
                        jaImportado, null));
            } else if (base != null && primeiraNoFlux.containsKey(base) && primeiraNoFlux.get(base) > parcela.numero()) {
                classificadas.add(new Classificada(linha, parcela, base, Situacao.FORA, Motivo.COMPRA_JA_NO_FLUX, false, futuro, null,
                        primeiraNoFlux.get(base)));
            } else if (base != null && menorDoArquivo.get(base) < parcela.numero()) {
                classificadas.add(new Classificada(linha, parcela, base, Situacao.FORA, Motivo.PARCELA_DO_ARQUIVO, false, futuro, null,
                        menorDoArquivo.get(base)));
            } else {
                ExistenteNoCartao parecido = parecido(linha, parcela, existentes, pareados, fatura);
                if (parecido != null) {
                    pareados.add(parecido.id());
                    classificadas.add(new Classificada(linha, parcela, base == null ? chave : base, Situacao.PROVAVEL, null, false, futuro,
                            parecido, null));
                } else {
                    classificadas.add(new Classificada(linha, parcela, base == null ? chave : base, Situacao.NOVA, null, !futuro, futuro,
                            null, null));
                }
            }
        }
        return classificadas;
    }

    /** O que deixa a linha de fora antes de olhar o banco, ou nulo. */
    static Motivo motivoDeFora(ExtratoLido.Linha linha, LocalDate ate, LocalDate desde) {
        if (!linha.legivel()) {
            return Motivo.ILEGIVEL;
        }
        if (linha.valor().signum() == 0) {
            return Motivo.VALOR_ZERO;
        }
        if (Classificador.linhaDeSaldo(linha.descricao()) || linhaDeTotal(linha.descricao())) {
            return Motivo.LINHA_DE_SALDO;
        }
        if (pagamento(linha)) {
            return Motivo.PAGAMENTO_DA_FATURA;
        }
        if (linha.data().isAfter(ate)) {
            return Motivo.DATA_ALEM_DO_LIMITE;
        }
        return linha.data().isBefore(desde) ? Motivo.DATA_ANTES_DO_LIMITE : null;
    }

    /**
     * Os centavos da divisão (decisão 15 e a escolha do dono): o resto fica numa parcela só, então as parcelas da mesma
     * compra em N diferem em até N − 1 centavos. À vista (N = 1), o valor é o mesmo.
     */
    static boolean mesmoValor(BigDecimal um, BigDecimal outro, int parcelas) {
        return um.subtract(outro).abs().compareTo(BigDecimal.valueOf(parcelas - 1L, 2)) <= 0;
    }

    /**
     * A base da compra da linha "Parcela k/N", entre a do ordinal dela e as dos ordinais de 1 em diante, nessa ordem:
     * primeiro a de uma compra que já está no Flux com os centavos da divisão; sem nenhuma, a primeira que não é de
     * compra do Flux. As duas pulam a que outra linha deste arquivo já tomou com o mesmo k. Assim a chave da linha nunca
     * repete a de outra compra, a mesma compra acha a mesma base em todo mês, e o export parcial seguido do completo
     * (que muda o ordinal) reencontra a compra.
     */
    private static String base(long cartaoId, Chaves.DaFatura daLinha, ParcelaNaDescricao.Parcela parcela, BigDecimal valor,
                               Map<String, BigDecimal> valorDaBase, Set<String> tomadas) {
        List<String> bases = new ArrayList<>();
        bases.add(daLinha.base());
        for (int ordinal = 1; ordinal < daLinha.ordinal() + ORDINAIS; ordinal++) {
            if (ordinal != daLinha.ordinal()) {
                bases.add(Chaves.baseDaParcela(cartaoId, daLinha.semente(), ordinal));
            }
        }
        for (String base : bases) {
            BigDecimal noFlux = valorDaBase.get(base);
            if (noFlux != null && mesmoValor(noFlux, valor, parcela.parcelas()) && !tomadas.contains(base + ":" + parcela.numero())) {
                return base;
            }
        }
        for (String base : bases) {
            if (!valorDaBase.containsKey(base) && !tomadas.contains(base + ":" + parcela.numero())) {
                return base;
            }
        }
        return daLinha.base();
    }

    /** Decisão 42: o crédito com "pagamento" na descrição ("Pagamento recebido") é o pagamento da fatura. */
    static boolean pagamento(ExtratoLido.Linha linha) {
        return linha.valor().signum() > 0 && TextoComparavel.chave(linha.descricao()).contains("pagamento");
    }

    /** Decisão 42: "Total", "Total da fatura", "Total desta fatura", "Total a pagar" e "Total geral". */
    static boolean linhaDeTotal(String descricao) {
        String chave = TextoComparavel.chave(descricao);
        return chave.equals("total") || chave.startsWith("total da fatura") || chave.startsWith("total desta fatura")
                || chave.startsWith("total a pagar") || chave.startsWith("total geral");
    }

    /** A salvaguarda da 04a: a chave do identificador só vale se o lançamento dela tem a mesma data da compra e o mesmo valor. */
    private static String chave(Chaves.DaFatura daLinha, Map<String, ExistenteNoCartao> porChave, ExtratoLido.Linha linha) {
        if (daLinha.doIdentificador() == null) {
            return daLinha.doConteudo();
        }
        ExistenteNoCartao peloIdentificador = porChave.get(daLinha.doIdentificador());
        return peloIdentificador == null || mesmaCompra(peloIdentificador, linha) ? daLinha.doIdentificador() : daLinha.doConteudo();
    }

    private static boolean mesmaCompra(ExistenteNoCartao existente, ExtratoLido.Linha linha) {
        return existente.dataCompra().equals(linha.data()) && existente.valor().compareTo(linha.valor()) == 0;
    }

    /** Decisões 41 e 43: com quem a linha se parece, ou nulo. */
    private static ExistenteNoCartao parecido(ExtratoLido.Linha linha, ParcelaNaDescricao.Parcela parcela, List<ExistenteNoCartao> existentes,
                                             Set<Long> pareados, YearMonth fatura) {
        String descricao = TextoComparavel.chave(parcela == null ? linha.descricao() : parcela.descricao());
        int quantas = parcela == null ? 1 : parcela.parcelas();
        for (ExistenteNoCartao existente : existentes) {
            if (pareados.contains(existente.id()) || !mesmoValor(existente.valor(), linha.valor(), quantas)) {
                continue;
            }
            boolean mesmaPosicao = parcela == null ? existente.parcelas() == 1 && existente.dataCompra().equals(linha.data())
                    : existente.numeroParcela() == parcela.numero() && existente.parcelas() == parcela.parcelas()
                    && existente.mes().equals(fatura);
            if (!mesmaPosicao) {
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
