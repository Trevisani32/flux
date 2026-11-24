package com.flux.importacao.interno;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.categorizacao.RegrasDeCategorizacao;
import com.flux.importacao.interno.Classificador.Situacao;
import com.flux.importacao.interno.dto.ConfirmarRequest;
import com.flux.importacao.interno.dto.PreviaResponse;
import com.flux.importacao.interno.dto.ResultadoResponse;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.importacao.interno.leitura.LeitorCsv;
import com.flux.importacao.interno.leitura.LeitorOfx;
import com.flux.importacao.interno.leitura.FormatoCsv;
import com.flux.importacao.interno.leitura.TextoDoArquivo;
import com.flux.lancamentos.LancamentosImportados;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Prévia e confirmação da importação da fatura de cartão. */
@Service
class ImportacaoDeFatura {

    private final ImportacaoRepository importacoes;
    private final PerfilCsvRepository perfis;
    private final LancamentosImportados importados;
    private final CartoesConsulta cartoes;
    private final ContasConsulta contas;
    private final RegrasDeCategorizacao regras;
    private final Clock clock;

    ImportacaoDeFatura(ImportacaoRepository importacoes, PerfilCsvRepository perfis, LancamentosImportados importados,
                        CartoesConsulta cartoes, ContasConsulta contas, RegrasDeCategorizacao regras, Clock clock) {
        this.importacoes = importacoes;
        this.perfis = perfis;
        this.importados = importados;
        this.cartoes = cartoes;
        this.contas = contas;
        this.regras = regras;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    PreviaResponse previa(Long usuarioId, Long cartaoId, String faturaPedida, MultipartFile arquivo) {
        byte[] bytes = bytes(arquivo);
        CartaoResumo cartao = cartaoAtivo(usuarioId, cartaoId, "importar nele");
        ExtratoLido extrato = ler(usuarioId, cartao.id(), bytes);
        LocalDate hoje = LocalDate.now(clock);
        LocalDate sugestao = sugerida(extrato, hoje);
        List<LancamentosImportados.FaturaParaImportar> oferecidas = importados.faturasParaImportar(usuarioId, cartao.id(),
                sugestao == null ? hoje : sugestao);
        YearMonth escolhida = faturaPedida == null ? oferecidas.get(1).mes() : mes(faturaPedida);
        if (oferecidas.stream().noneMatch(fatura -> fatura.mes().equals(escolhida))) {
            throw new RegraDeNegocioException("Escolha uma das faturas oferecidas.", "FATURA_INVALIDA").com("campo", "fatura");
        }
        List<ClassificadorDaFatura.Classificada> classificadas = classificar(usuarioId, cartao.id(), escolhida, extrato, hoje);
        Map<Integer, RegrasDeCategorizacao.Sugestao> sugestoes = sugestoes(usuarioId, cartao.id(), classificadas);
        List<PreviaResponse.Linha> linhas = classificadas.stream().sorted(Comparator.comparing(
                        (ClassificadorDaFatura.Classificada linha) -> linha.linha().data(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(linha -> linha.linha().numero()).reversed()).map(linha -> linha(linha, sugestoes.get(linha.linha().numero())))
                .toList();
        List<LocalDate> datas = extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).map(ExtratoLido.Linha::data).toList();
        String hash = Chaves.sha256(bytes);
        return new PreviaResponse(hash, extrato.formato().name(), nome(arquivo), null, null, null,
                datas.stream().min(Comparator.naturalOrder()).orElse(null), datas.stream().max(Comparator.naturalOrder()).orElse(null),
                linhas, arquivoRepetido(usuarioId, cartao, hash), null, perfil(usuarioId, cartao.id(), extrato),
                origens(usuarioId, classificadas), cartao.id(),
                cartao.nome(), fatura(oferecidas.stream().filter(item -> item.mes().equals(escolhida)).findFirst().orElseThrow()),
                oferecidas.stream().map(ImportacaoDeFatura::fatura).toList(), sugestao);
    }

    @Transactional
    ResultadoResponse confirmar(Long usuarioId, MultipartFile arquivo, ConfirmarRequest pedido) {
        byte[] bytes = bytes(arquivo);
        if (!Chaves.sha256(bytes).equals(pedido.hash())) {
            throw new RegraDeNegocioException("O arquivo mudou desde a revisão. Escolha o arquivo de novo.", "ARQUIVO_DIFERENTE");
        }
        if (pedido.fatura() == null) {
            throw new CampoInvalidoException("fatura", "Escolha a fatura.");
        }
        cartoes.travar(usuarioId);
        CartaoResumo cartao = cartaoAtivo(usuarioId, pedido.cartaoId(), "importar nele");
        ExtratoLido extrato = ler(usuarioId, cartao.id(), bytes);
        LocalDate hoje = LocalDate.now(clock);
        LocalDate sugestao = sugerida(extrato, hoje);
        List<LancamentosImportados.FaturaParaImportar> oferecidas = importados.faturasParaImportar(usuarioId, cartao.id(),
                sugestao == null ? hoje : sugestao);
        YearMonth escolhida = mes(pedido.fatura());
        if (oferecidas.stream().noneMatch(fatura -> fatura.mes().equals(escolhida))) {
            throw new RegraDeNegocioException("Escolha uma das faturas oferecidas.", "FATURA_INVALIDA").com("campo", "fatura");
        }
        Map<Integer, ClassificadorDaFatura.Classificada> porLinha = classificar(usuarioId, cartao.id(), escolhida, extrato, hoje).stream()
                .collect(Collectors.toMap(item -> item.linha().numero(), Function.identity()));
        Set<Integer> vistas = new HashSet<>();
        List<LancamentosImportados.NovaNaFatura> novas = new ArrayList<>();
        List<LancamentosImportados.ValorDoBanco> valores = new ArrayList<>();
        int porOutra = 0;
        for (ConfirmarRequest.Escolha escolha : pedido.linhas()) {
            ClassificadorDaFatura.Classificada linha = porLinha.get(escolha.linha());
            if (linha == null || !vistas.add(escolha.linha())) {
                continue;
            }
            if (linha.valorNoFlux() != null) {
                valores.add(new LancamentosImportados.ValorDoBanco(linha.parecido().id(), linha.linha().valor()));
            } else if (linha.situacao() == Situacao.JA_IMPORTADA) {
                porOutra++;
            } else if (linha.importavel()) {
                novas.add(new LancamentosImportados.NovaNaFatura(linha.linha().numero(), linha.linha().data(), linha.linha().valor(),
                        escolha.descricao() == null || escolha.descricao().isBlank() ? linha.descricaoDoExtrato() : escolha.descricao(),
                        linha.descricaoDoExtrato(), linha.chave(), linha.parcela() == null ? null : linha.parcela().numero(),
                        linha.parcela() == null ? null : linha.parcela().parcelas(), escolha.categoriaId()));
            }
        }
        int corrigidas = importados.corrigirParcelas(usuarioId, valores);
        Long importacaoId = null;
        int lancamentos = 0;
        int importaveis = Math.toIntExact(porLinha.values().stream().filter(linha -> linha.situacao() == Situacao.NOVA
                || linha.situacao() == Situacao.PROVAVEL).count());
        int desmarcadas = importaveis - novas.size();
        if (!novas.isEmpty()) {
            Long faturaId = importados.faturaParaImportar(usuarioId, cartao.id(), escolhida);
            Map<Situacao, Long> situacoes = porLinha.values().stream().collect(Collectors.groupingBy(
                    ClassificadorDaFatura.Classificada::situacao, Collectors.counting()));
            List<LocalDate> datas = extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).map(ExtratoLido.Linha::data).toList();
            Importacao.Quantidades quantidades = new Importacao.Quantidades(porLinha.size(), novas.size(),
                    situacoes.getOrDefault(Situacao.JA_IMPORTADA, 0L).intValue(), 0,
                    situacoes.getOrDefault(Situacao.FORA, 0L).intValue(), desmarcadas);
            Importacao importacao = importacoes.save(Importacao.daFatura(usuarioId, cartao.id(), faturaId, extrato.formato(), nome(arquivo),
                    pedido.hash(), datas.stream().min(Comparator.naturalOrder()).orElse(hoje),
                    datas.stream().max(Comparator.naturalOrder()).orElse(hoje), quantidades, agora()));
            importacaoId = importacao.getId();
            lancamentos = importados.gravarNaFatura(usuarioId, cartao.id(), escolhida, importacaoId, novas);
        }
        List<ResultadoResponse.Parcelada> parceladas = novas.stream().filter(nova -> nova.parcelas() != null)
                .map(nova -> new ResultadoResponse.Parcelada(nova.descricao(), nova.parcela(), nova.parcelas())).toList();
        long deFora = porLinha.values().stream().filter(linha -> linha.situacao() == Situacao.FORA).count();
        long ja = porLinha.values().stream().filter(linha -> linha.situacao() == Situacao.JA_IMPORTADA).count();
        return new ResultadoResponse(importacaoId, null, null, novas.size(), desmarcadas, (int) ja - porOutra - corrigidas, (int) deFora,
                porOutra, null, null,
                List.of(), cartao.id(), cartao.nome(), escolhida.toString(), lancamentos, parceladas, corrigidas);
    }

    void desfazer(Long usuarioId, Importacao importacao) {
        cartaoAtivo(usuarioId, importacao.getCartaoId(), "desfazer esta importação");
        importados.desfazerDaFatura(usuarioId, importacao.getId());
    }

    private List<ClassificadorDaFatura.Classificada> classificar(Long usuarioId, Long cartaoId, YearMonth fatura, ExtratoLido extrato,
                                                                  LocalDate hoje) {
        List<LocalDate> datas = extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).map(ExtratoLido.Linha::data).toList();
        Map<Integer, ParcelaNaDescricao.Parcela> parcelas = ClassificadorDaFatura.parcelas(extrato.linhas());
        Map<Integer, Chaves.DaFatura> chaves = Chaves.daFatura(cartaoId, extrato.linhas(), parcelas);
        List<LancamentosImportados.ExistenteNoCartao> existentes = datas.isEmpty() ? List.of() : importados.existentesNoCartao(usuarioId,
                cartaoId, fatura, datas.stream().min(Comparator.naturalOrder()).orElseThrow(),
                datas.stream().max(Comparator.naturalOrder()).orElseThrow(), Chaves.todasDaFatura(chaves));
        return ClassificadorDaFatura.classificar(cartaoId, extrato.linhas(), existentes, fatura, hoje);
    }

    private ExtratoLido ler(Long usuarioId, Long cartaoId, byte[] bytes) {
        if (TextoDoArquivo.binario(bytes)) {
            throw new RegraDeNegocioException("Não reconhecemos este arquivo. Envie a fatura do cartão em OFX ou CSV.", "ARQUIVO_INVALIDO");
        }
        String texto = TextoDoArquivo.decodificar(bytes);
        ExtratoLido extrato;
        if (TextoDoArquivo.ofx(texto)) {
            extrato = new LeitorOfx().ler(texto);
            if (!extrato.cartao()) {
                throw new RegraDeNegocioException("Este arquivo é o extrato de uma conta: escolha uma conta no lugar do cartão.",
                        "EXTRATO_DE_CONTA");
            }
        } else {
            if (TextoDoArquivo.cabecalhoDoNubank(texto)) {
                throw new RegraDeNegocioException("Este arquivo é o extrato de uma conta: escolha uma conta no lugar do cartão.",
                        "EXTRATO_DE_CONTA");
            }
            PerfilCsv perfil = perfis.findByCartaoIdAndUsuarioId(cartaoId, usuarioId)
                    .orElseThrow(() -> new RegraDeNegocioException("Este cartão ainda não tem perfil de CSV.", "PERFIL_NECESSARIO"));
            extrato = new LeitorCsv(perfil.formato()).ler(texto);
        }
        if (extrato.linhas().isEmpty()) {
            throw new RegraDeNegocioException("O arquivo não tem nenhuma movimentação.", "ARQUIVO_VAZIO");
        }
        if (extrato.linhas().size() > ImportacaoService.LIMITE_DE_LINHAS) {
            throw new RegraDeNegocioException("O arquivo tem movimentações demais.", "ARQUIVO_GRANDE");
        }
        return extrato;
    }

    private LocalDate sugerida(ExtratoLido extrato, LocalDate hoje) {
        Map<Integer, ParcelaNaDescricao.Parcela> parcelas = ClassificadorDaFatura.parcelas(extrato.linhas());
        return extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).filter(linha -> !parcelas.containsKey(linha.numero()))
                .filter(linha -> ClassificadorDaFatura.motivoDeFora(linha, hoje.plusMonths(12), hoje.minusYears(10)) == null)
                .map(ExtratoLido.Linha::data).max(Comparator.naturalOrder()).orElse(null);
    }

    private CartaoResumo cartaoAtivo(Long usuarioId, Long cartaoId, String acao) {
        if (cartaoId == null) {
            throw new CampoInvalidoException("contaId", "Escolha uma conta ou um cartão.");
        }
        CartaoResumo cartao = cartoes.buscar(usuarioId, cartaoId)
                .orElseThrow(() -> new RegraDeNegocioException("Cartão não encontrado."));
        if (cartao.arquivado()) {
            throw new RegraDeNegocioException(cartao.nome() + " está arquivado. Reative o cartão para " + acao + ".", "CARTAO_ARQUIVADO");
        }
        return cartao;
    }

    private PreviaResponse.ArquivoRepetido arquivoRepetido(Long usuarioId, CartaoResumo cartao, String hash) {
        return importacoes.findFirstByUsuarioIdAndHashArquivoAndDesfeitaEmIsNullOrderByCriadaEmDesc(usuarioId, hash).map(anterior ->
                new PreviaResponse.ArquivoRepetido(anterior.getCriadaEm(), anterior.getContaId(), anterior.getCartaoId(),
                        anterior.daFatura() ? cartoes.buscar(usuarioId, anterior.getCartaoId()).map(CartaoResumo::nome).orElse("")
                                : contas.buscar(usuarioId, anterior.getContaId()).map(ContaResumo::nome).orElse(""),
                        java.util.Objects.equals(anterior.getContaId(), null)
                                && java.util.Objects.equals(anterior.getCartaoId(), cartao.id())))
                .orElse(null);
    }

    private List<PreviaResponse.Origem> origens(Long usuarioId, List<ClassificadorDaFatura.Classificada> classificadas) {
        Set<Long> ids = classificadas.stream()
                .filter(classificada -> classificada.situacao() == Situacao.JA_IMPORTADA && classificada.parecido() != null)
                .map(classificada -> classificada.parecido().importacaoId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? List.of() : importacoes.findAllByIdInAndUsuarioIdOrderByCriadaEmDescIdDesc(ids, usuarioId).stream()
                .map(importacao -> new PreviaResponse.Origem(importacao.getNomeArquivo(), importacao.getCriadaEm()))
                .toList();
    }

    private FormatoCsv perfil(Long usuarioId, Long cartaoId, ExtratoLido extrato) {
        return extrato.formato() == ExtratoLido.Formato.OFX ? null
                : perfis.findByCartaoIdAndUsuarioId(cartaoId, usuarioId).map(PerfilCsv::formato).orElse(null);
    }

    private Map<Integer, RegrasDeCategorizacao.Sugestao> sugestoes(Long usuarioId, Long cartaoId,
                                                                    List<ClassificadorDaFatura.Classificada> classificadas) {
        List<ClassificadorDaFatura.Classificada> importaveis = classificadas.stream()
                .filter(ClassificadorDaFatura.Classificada::importavel).toList();
        List<RegrasDeCategorizacao.Sugestao> encontradas = regras.sugerir(usuarioId, null, cartaoId, importaveis.stream()
                .map(item -> new RegrasDeCategorizacao.Alvo(item.descricaoDoExtrato(), item.linha().valor())).toList());
        Map<Integer, RegrasDeCategorizacao.Sugestao> porLinha = new HashMap<>();
        for (int indice = 0; indice < importaveis.size(); indice++) {
            if (encontradas.get(indice) != null) {
                porLinha.put(importaveis.get(indice).linha().numero(), encontradas.get(indice));
            }
        }
        return porLinha;
    }

    private static PreviaResponse.Linha linha(ClassificadorDaFatura.Classificada classificada,
                                              RegrasDeCategorizacao.Sugestao sugestao) {
        ExtratoLido.Linha linha = classificada.linha();
        var parecido = classificada.parecido();
        return new PreviaResponse.Linha(linha.numero(), linha.data(), linha.descricao(), linha.valor(), classificada.situacao().name(),
                classificada.motivo() == null ? null : classificada.motivo().name(), classificada.marcada(), classificada.futuro(), linha.erro(),
                parecido == null ? null : new PreviaResponse.Parecido(parecido.descricao(), parecido.dataCompra(), parecido.manual(), false,
                        parecido.criadoEm(), null), sugestao == null ? null : new PreviaResponse.Sugestao(sugestao.regraId(),
                        sugestao.posicao(), sugestao.condicao(), sugestao.texto(), sugestao.categoriaId(), sugestao.descricao()),
                        classificada.parcela() == null ? null : classificada.parcela().numero(),
                classificada.parcela() == null ? null : classificada.parcela().parcelas(), classificada.parcelaQueCobre(),
                classificada.descricaoDoExtrato(), classificada.valorNoFlux());
    }

    private static PreviaResponse.Fatura fatura(LancamentosImportados.FaturaParaImportar fatura) {
        return new PreviaResponse.Fatura(fatura.mes().toString(), fatura.fechamento(), fatura.vencimento(), fatura.situacao());
    }

    private static YearMonth mes(String texto) {
        try { return YearMonth.parse(texto); } catch (RuntimeException ex) { throw new CampoInvalidoException("fatura", "Mês inválido."); }
    }

    private static byte[] bytes(MultipartFile arquivo) {
        try { return arquivo.getBytes(); } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    private static String nome(MultipartFile arquivo) {
        String nome = java.util.Objects.requireNonNullElse(arquivo.getOriginalFilename(), "extrato").replace('\\', '/');
        return nome.substring(nome.lastIndexOf('/') + 1);
    }

    private Instant agora() { return Instant.now(clock).truncatedTo(ChronoUnit.MICROS); }
}
