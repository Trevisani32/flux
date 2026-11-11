package com.flux.importacao.interno;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorizacao.RegrasDeCategorizacao;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.contas.MovimentoDasContas;
import com.flux.importacao.interno.Classificador.Classificada;
import com.flux.importacao.interno.Classificador.Situacao;
import com.flux.importacao.interno.dto.ConfirmarRequest;
import com.flux.importacao.interno.dto.ImportacaoResponse;
import com.flux.importacao.interno.dto.PreviaResponse;
import com.flux.importacao.interno.dto.ResultadoResponse;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.importacao.interno.leitura.FormatoCsv;
import com.flux.importacao.interno.leitura.LeitorCsv;
import com.flux.importacao.interno.leitura.LeitorOfx;
import com.flux.importacao.interno.leitura.TextoDoArquivo;
import com.flux.lancamentos.LancamentosImportados;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Revisão, confirmação, histórico e desfazer (ADR 0006 e ADR 0014). A revisão não grava nada; a
 * confirmação recebe o arquivo de novo, pega a trava de contas, classifica outra vez e só então
 * grava, pela API de lançamentos (decisões 13 e 38). Na revisão, as regras de categorização (Fatia 04b)
 * sugerem a categoria e a descrição de cada linha para importar; a confirmação grava o que a revisão
 * mostrou, sem rodar as regras de novo (decisões 14 e 15 da 04b).
 */
@Service
public class ImportacaoService {

    static final int LIMITE_DE_LINHAS = 3_000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ImportacaoRepository importacoes;
    private final PerfilCsvRepository perfis;
    private final LancamentosImportados importados;
    private final ContasConsulta contas;
    private final MovimentoDasContas movimento;
    private final RegrasDeCategorizacao regras;
    private final Clock clock;
    /** A chave do HMAC do extrato (decisão 11), derivada do segredo do servidor com um rótulo próprio. */
    private final byte[] chaveDoExtrato;

    public ImportacaoService(ImportacaoRepository importacoes, PerfilCsvRepository perfis, LancamentosImportados importados,
                             ContasConsulta contas, MovimentoDasContas movimento, RegrasDeCategorizacao regras, Clock clock,
                             @Value("${app.jwt.segredo}") String segredo) {
        this.importacoes = importacoes;
        this.perfis = perfis;
        this.importados = importados;
        this.contas = contas;
        this.movimento = movimento;
        this.regras = regras;
        this.clock = clock;
        this.chaveDoExtrato = Chaves.hmac(segredo.getBytes(StandardCharsets.UTF_8), "flux:importacao:extrato");
    }

    @Transactional(readOnly = true)
    public PreviaResponse previa(Long usuarioId, Long contaId, MultipartFile arquivo) {
        byte[] bytes = bytes(arquivo);
        ContaResumo conta = contaParaImportar(usuarioId, contaId);
        ExtratoLido extrato = ler(usuarioId, conta.id(), bytes);
        String hash = Chaves.sha256(bytes);
        List<Classificada> classificadas = classificar(usuarioId, conta, extrato);
        Map<Integer, RegrasDeCategorizacao.Sugestao> sugestoes = sugestoes(usuarioId, conta, classificadas);
        List<PreviaResponse.Linha> linhas = classificadas.stream()
                .sorted(Comparator.comparing((Classificada c) -> c.linha().data(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingInt(c -> c.linha().numero()).reversed())
                .map(classificada -> linhaDaPrevia(classificada, sugestoes.get(classificada.linha().numero())))
                .toList();
        List<LocalDate> datas = extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).map(ExtratoLido.Linha::data).toList();
        return new PreviaResponse(hash, extrato.formato().name(), nomeDoArquivo(arquivo), conta.id(), conta.nome(),
                conta.dataSaldoInicial(), datas.stream().min(Comparator.naturalOrder()).orElse(null),
                datas.stream().max(Comparator.naturalOrder()).orElse(null), linhas, arquivoRepetido(usuarioId, conta, hash),
                outraConta(usuarioId, conta, extrato), perfilUsado(usuarioId, conta, extrato), origens(usuarioId, classificadas));
    }

    /**
     * A confirmação (decisões 13, 24 e 38): o mesmo arquivo da revisão, a trava de contas, a
     * classificação de novo e a gravação. Linha marcada que outra importação gravou no meio do
     * caminho fica de fora e é contada em {@code entraramPorOutra}, e não em {@code jaImportadas} (o
     * registro conta as duas em {@code duplicadas}, que fecha a soma de {@code lidas}).
     */
    @Transactional
    public ResultadoResponse confirmar(Long usuarioId, MultipartFile arquivo, ConfirmarRequest pedido) {
        byte[] bytes = bytes(arquivo);
        String hash = Chaves.sha256(bytes);
        if (!hash.equals(pedido.hash())) {
            throw new RegraDeNegocioException("O arquivo mudou desde a revisão. Escolha o arquivo de novo.", "ARQUIVO_DIFERENTE");
        }
        contas.travar(usuarioId);
        ContaResumo conta = contaParaImportar(usuarioId, pedido.contaId());
        ExtratoLido extrato = ler(usuarioId, conta.id(), bytes);
        Map<Integer, Classificada> porLinha = classificar(usuarioId, conta, extrato).stream()
                .collect(Collectors.toMap(c -> c.linha().numero(), Function.identity()));
        Set<Integer> vistas = new HashSet<>();
        List<LancamentosImportados.Novo> novos = new ArrayList<>();
        int entraramPorOutra = 0;
        for (ConfirmarRequest.Escolha escolha : pedido.linhas()) {
            Classificada classificada = porLinha.get(escolha.linha());
            if (classificada == null || !vistas.add(escolha.linha())) {
                continue;
            }
            if (classificada.situacao() == Situacao.JA_IMPORTADA) {
                entraramPorOutra++;
            } else if (classificada.importavel()) {
                ExtratoLido.Linha linha = classificada.linha();
                String descricao = escolha.descricao() == null || escolha.descricao().isBlank() ? linha.descricao()
                        : TextoComparavel.limparEspacos(escolha.descricao());
                novos.add(new LancamentosImportados.Novo(linha.numero(), linha.data(), linha.valor(), descricao, linha.descricao(),
                        classificada.chave(), escolha.categoriaId()));
            }
        }
        Map<Situacao, Long> porSituacao = porLinha.values().stream()
                .collect(Collectors.groupingBy(Classificada::situacao, Collectors.counting()));
        int importaveis = (int) (contar(porSituacao, Situacao.NOVA) + contar(porSituacao, Situacao.PROVAVEL));
        Importacao.Quantidades quantidades = new Importacao.Quantidades(porLinha.size(), novos.size(),
                (int) contar(porSituacao, Situacao.JA_IMPORTADA), (int) contar(porSituacao, Situacao.ANTES_DO_SALDO_INICIAL),
                (int) contar(porSituacao, Situacao.FORA), importaveis - novos.size());
        Long importacaoId = null;
        if (!novos.isEmpty()) {
            List<LocalDate> datas = extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).map(ExtratoLido.Linha::data).toList();
            Importacao importacao = importacoes.save(new Importacao(usuarioId, conta.id(), extrato.formato(), nomeDoArquivo(arquivo),
                    hash, extratoHash(usuarioId, extrato), finalDaConta(extrato), datas.stream().min(Comparator.naturalOrder()).orElseThrow(),
                    datas.stream().max(Comparator.naturalOrder()).orElseThrow(), quantidades, agora()));
            importados.gravar(usuarioId, conta.id(), importacao.getId(), novos);
            importacaoId = importacao.getId();
        }
        String mes = novos.stream().map(LancamentosImportados.Novo::data).max(Comparator.naturalOrder())
                .map(data -> YearMonth.from(data).toString()).orElse(null);
        return new ResultadoResponse(importacaoId, conta.id(), conta.nome(), novos.size(), quantidades.desmarcadas(),
                quantidades.duplicadas() - entraramPorOutra, quantidades.anteriores() + quantidades.fora(), entraramPorOutra,
                saldoDeHoje(usuarioId, conta), mes);
    }

    @Transactional(readOnly = true)
    public List<ImportacaoResponse> listar(Long usuarioId) {
        Map<Long, ContaResumo> doUsuario = contas.listar(usuarioId).stream()
                .collect(Collectors.toMap(ContaResumo::id, Function.identity()));
        Map<Long, LancamentosImportados.Contagem> contagens = importados.contagens(usuarioId).stream()
                .collect(Collectors.toMap(LancamentosImportados.Contagem::importacaoId, Function.identity()));
        return importacoes.findAllByUsuarioIdOrderByCriadaEmDescIdDesc(usuarioId).stream()
                .map(importacao -> resposta(importacao, doUsuario.get(importacao.getContaId()), contagens.get(importacao.getId())))
                .toList();
    }

    /**
     * Desfazer (decisão 32): apaga todos os lançamentos da importação, inclusive editados e
     * ignorados, e marca o registro. Com a conta arquivada, é recusado.
     */
    @Transactional
    public ImportacaoResponse desfazer(Long usuarioId, Long id) {
        contas.travar(usuarioId);
        Importacao importacao = encontrar(usuarioId, id);
        if (importacao.desfeita()) {
            throw new RegraDeNegocioException("Esta importação já foi desfeita.", "IMPORTACAO_DESFEITA");
        }
        ContaResumo conta = contas.buscar(usuarioId, importacao.getContaId()).orElseThrow();
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Reative a conta para desfazer esta importação.",
                    "CONTA_ARQUIVADA");
        }
        importados.desfazer(usuarioId, id);
        importacao.desfazer(agora());
        return resposta(importacao, conta, null);
    }

    @Transactional(readOnly = true)
    public List<LancamentosImportados.Excluido> excluidos(Long usuarioId, Long id) {
        return importados.excluidos(usuarioId, encontrar(usuarioId, id).getId());
    }

    private Importacao encontrar(Long usuarioId, Long id) {
        return importacoes.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Importação não encontrada."));
    }

    private static byte[] bytes(MultipartFile arquivo) {
        try {
            byte[] bytes = arquivo.getBytes();
            if (bytes.length == 0) {
                throw new RegraDeNegocioException("O arquivo não tem nenhuma movimentação.", "ARQUIVO_VAZIO");
            }
            return bytes;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Conta do usuário (ADR 0007) e ativa. */
    private ContaResumo contaParaImportar(Long usuarioId, Long contaId) {
        ContaResumo conta = contas.buscar(usuarioId, contaId)
                .orElseThrow(() -> new RegraDeNegocioException("Conta não encontrada."));
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Reative a conta para importar nela.", "CONTA_ARQUIVADA");
        }
        return conta;
    }

    /** O arquivo pelo conteúdo, não pela extensão (decisões 5, 10 e 23). */
    private ExtratoLido ler(Long usuarioId, Long contaId, byte[] bytes) {
        if (TextoDoArquivo.binario(bytes)) {
            throw new RegraDeNegocioException("Não reconhecemos este arquivo. Envie o extrato da conta em OFX ou CSV.",
                    "ARQUIVO_INVALIDO");
        }
        String texto = TextoDoArquivo.decodificar(bytes);
        ExtratoLido extrato;
        if (TextoDoArquivo.ofx(texto)) {
            extrato = new LeitorOfx().ler(texto);
            int extratos = extrato.contas() + (extrato.cartao() ? 1 : 0);
            if (extrato.cartao() && extrato.contas() == 0) {
                throw new RegraDeNegocioException("Este arquivo é a fatura de um cartão de crédito. A importação de faturas chega junto "
                        + "com os cartões; por enquanto, importe o extrato da conta.", "EXTRATO_DE_CARTAO");
            }
            if (extratos > 1) {
                throw new RegraDeNegocioException("Este arquivo tem extratos de " + extratos + " contas. Exporte uma conta por arquivo.",
                        "VARIAS_CONTAS");
            }
        } else {
            if (TextoDoArquivo.faturaDoNubank(texto)) {
                throw new RegraDeNegocioException("Este CSV parece ser a fatura do cartão Nubank. A importação de faturas chega junto "
                        + "com os cartões.", "EXTRATO_DE_CARTAO");
            }
            PerfilCsv perfil = perfis.findByContaIdAndUsuarioId(contaId, usuarioId)
                    .orElseThrow(() -> new RegraDeNegocioException("Esta conta ainda não tem perfil de CSV.", "PERFIL_NECESSARIO"));
            extrato = new LeitorCsv(perfil.formato()).ler(texto);
            if (!extrato.linhas().isEmpty() && extrato.linhas().stream().noneMatch(ExtratoLido.Linha::legivel)) {
                throw new RegraDeNegocioException("Nenhuma linha pôde ser lida com este perfil. Confira as colunas e o formato da data.",
                        "PERFIL_NAO_LE");
            }
        }
        if (extrato.linhas().isEmpty()) {
            throw new RegraDeNegocioException("O arquivo não tem nenhuma movimentação.", "ARQUIVO_VAZIO");
        }
        if (extrato.linhas().size() > LIMITE_DE_LINHAS) {
            throw new RegraDeNegocioException("O arquivo tem " + String.format(PT_BR, "%,d", extrato.linhas().size())
                    + " movimentações. Importe no máximo " + String.format(PT_BR, "%,d", LIMITE_DE_LINHAS)
                    + " por vez: exporte um período menor.", "ARQUIVO_GRANDE");
        }
        return extrato;
    }

    private List<Classificada> classificar(Long usuarioId, ContaResumo conta, ExtratoLido extrato) {
        Map<Integer, Chaves.DaLinha> chaves = Chaves.doArquivo(conta.id(), extrato.linhas());
        List<LocalDate> datas = extrato.linhas().stream().filter(ExtratoLido.Linha::legivel).map(ExtratoLido.Linha::data).toList();
        List<LancamentosImportados.Existente> existentes = datas.isEmpty() ? List.of()
                : importados.existentes(usuarioId, conta.id(), datas.stream().min(Comparator.naturalOrder()).orElseThrow(),
                        datas.stream().max(Comparator.naturalOrder()).orElseThrow(), Chaves.todas(chaves));
        return Classificador.classificar(conta.id(), extrato.linhas(), existentes, conta.dataSaldoInicial(), LocalDate.now(clock));
    }

    /**
     * Decisão 14 da 04b: só as linhas para importar (novas e prováveis, inclusive as futuras) recebem a
     * sugestão da primeira regra que casa. Por número da linha; a linha sem regra não entra no mapa.
     */
    private Map<Integer, RegrasDeCategorizacao.Sugestao> sugestoes(Long usuarioId, ContaResumo conta, List<Classificada> classificadas) {
        List<ExtratoLido.Linha> importaveis = classificadas.stream().filter(Classificada::importavel).map(Classificada::linha).toList();
        if (importaveis.isEmpty()) {
            return Map.of();
        }
        List<RegrasDeCategorizacao.Sugestao> doArquivo = regras.sugerir(usuarioId, conta.id(), importaveis.stream()
                .map(linha -> new RegrasDeCategorizacao.Alvo(linha.descricao(), linha.valor())).toList());
        Map<Integer, RegrasDeCategorizacao.Sugestao> porLinha = new HashMap<>();
        for (int indice = 0; indice < importaveis.size(); indice++) {
            if (doArquivo.get(indice) != null) {
                porLinha.put(importaveis.get(indice).numero(), doArquivo.get(indice));
            }
        }
        return porLinha;
    }

    private static PreviaResponse.Linha linhaDaPrevia(Classificada classificada, RegrasDeCategorizacao.Sugestao sugestao) {
        ExtratoLido.Linha linha = classificada.linha();
        LancamentosImportados.Existente parecido = classificada.parecido();
        return new PreviaResponse.Linha(linha.numero(), linha.data(), linha.descricao(), linha.valor(),
                classificada.situacao().name(), classificada.motivo() == null ? null : classificada.motivo().name(),
                classificada.marcada(), classificada.futuro(), linha.erro(),
                parecido == null ? null : new PreviaResponse.Parecido(parecido.descricao(), parecido.data(), parecido.manual(),
                        parecido.ignorado(), parecido.criadoEm(), parecido.ignoradoEm()),
                sugestao == null ? null : new PreviaResponse.Sugestao(sugestao.regraId(), sugestao.posicao(), sugestao.condicao(),
                        sugestao.texto(), sugestao.categoriaId(), sugestao.descricao()));
    }

    /** O perfil de CSV que leu o arquivo (decisão 12: a tela mostra o resumo); nulo no OFX. */
    private FormatoCsv perfilUsado(Long usuarioId, ContaResumo conta, ExtratoLido extrato) {
        return extrato.formato() == ExtratoLido.Formato.OFX ? null
                : perfis.findByContaIdAndUsuarioId(conta.id(), usuarioId).map(PerfilCsv::formato).orElse(null);
    }

    /** As importações de onde vieram as linhas já importadas, a mais recente primeiro. */
    private List<PreviaResponse.Origem> origens(Long usuarioId, List<Classificada> classificadas) {
        Set<Long> ids = classificadas.stream()
                .filter(classificada -> classificada.situacao() == Situacao.JA_IMPORTADA && classificada.parecido() != null)
                .map(classificada -> classificada.parecido().importacaoId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? List.of() : importacoes.findAllByIdInAndUsuarioIdOrderByCriadaEmDescIdDesc(ids, usuarioId).stream()
                .map(importacao -> new PreviaResponse.Origem(importacao.getNomeArquivo(), importacao.getCriadaEm()))
                .toList();
    }

    private PreviaResponse.ArquivoRepetido arquivoRepetido(Long usuarioId, ContaResumo conta, String hash) {
        return importacoes.findFirstByUsuarioIdAndHashArquivoAndDesfeitaEmIsNullOrderByCriadaEmDesc(usuarioId, hash)
                .map(anterior -> new PreviaResponse.ArquivoRepetido(anterior.getCriadaEm(), anterior.getContaId(),
                        nomeDaConta(usuarioId, anterior.getContaId()), anterior.getContaId().equals(conta.id())))
                .orElse(null);
    }

    /**
     * Decisão 11: a importação mais recente do mesmo extrato numa outra conta ativa. A conta arquivada
     * fica de fora: "Importar em X" levaria a um CONTA_ARQUIVADA, numa conta que nem está no seletor.
     */
    private PreviaResponse.OutraConta outraConta(Long usuarioId, ContaResumo conta, ExtratoLido extrato) {
        String extratoHash = extratoHash(usuarioId, extrato);
        if (extratoHash == null) {
            return null;
        }
        for (Importacao anterior : importacoes.findAllByUsuarioIdAndExtratoHashAndDesfeitaEmIsNullOrderByCriadaEmDescIdDesc(usuarioId,
                extratoHash)) {
            Optional<ContaResumo> outra = anterior.getContaId().equals(conta.id()) ? Optional.empty()
                    : contas.buscar(usuarioId, anterior.getContaId()).filter(resumo -> !resumo.arquivada());
            if (outra.isPresent()) {
                return new PreviaResponse.OutraConta(anterior.getContaId(), outra.get().nome(), anterior.getExtratoFinal());
            }
        }
        return null;
    }

    private String nomeDaConta(Long usuarioId, Long contaId) {
        return contas.buscar(usuarioId, contaId).map(ContaResumo::nome).orElse("");
    }

    /**
     * Decisão 11: nunca o número da conta. Um SHA-256 puro de BANKID e ACCTID seria revertido por
     * força bruta (o espaço de contas é pequeno, e os 4 últimos dígitos ficam ao lado); o HMAC com a
     * chave do servidor não, sem o segredo (ADR 0014). O usuário entra na mensagem: o mesmo extrato
     * dá hashes diferentes para usuários diferentes.
     */
    private String extratoHash(Long usuarioId, ExtratoLido extrato) {
        return temExtrato(extrato)
                ? Chaves.hex(Chaves.hmac(chaveDoExtrato, usuarioId + "|" + extrato.bancoId().strip() + "|" + extrato.contaDoBanco().strip()))
                : null;
    }

    private static boolean temExtrato(ExtratoLido extrato) {
        return extrato.bancoId() != null && extrato.contaDoBanco() != null;
    }

    /** Os 4 últimos dígitos do ACCTID ("99999999-9" → "9999"). */
    private static String finalDaConta(ExtratoLido extrato) {
        if (!temExtrato(extrato)) {
            return null;
        }
        String digitos = extrato.contaDoBanco().replaceAll("\\D", "");
        String base = digitos.isEmpty() ? extrato.contaDoBanco().strip() : digitos;
        return base.substring(Math.max(0, base.length() - 4));
    }

    private BigDecimal saldoDeHoje(Long usuarioId, ContaResumo conta) {
        BigDecimal soma = movimento.somaPorContaAte(usuarioId, LocalDate.now(clock)).getOrDefault(conta.id(), BigDecimal.ZERO);
        return conta.saldoInicial().add(soma).setScale(2);
    }

    private static ImportacaoResponse resposta(Importacao importacao, ContaResumo conta, LancamentosImportados.Contagem contagem) {
        return new ImportacaoResponse(importacao.getId(), importacao.getNomeArquivo(), importacao.getContaId(),
                conta == null ? "" : conta.nome(), conta != null && conta.arquivada(), importacao.getCriadaEm(),
                importacao.getDataInicio(), importacao.getDataFim(), importacao.getImportadas(), importacao.getDuplicadas(),
                importacao.getDesmarcadas(), importacao.getAnteriores() + importacao.getFora(),
                contagem == null ? 0 : contagem.editados(), contagem == null ? 0 : contagem.excluidos(), importacao.getDesfeitaEm());
    }

    /** O nome que o navegador mandou, sem pasta, até 255 caracteres. */
    private static String nomeDoArquivo(MultipartFile arquivo) {
        String nome = Objects.requireNonNullElse(arquivo.getOriginalFilename(), "").replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).strip();
        return nome.isEmpty() ? "extrato" : nome.substring(0, Math.min(255, nome.length()));
    }

    private static long contar(Map<Situacao, Long> porSituacao, Situacao situacao) {
        return porSituacao.getOrDefault(situacao, 0L);
    }

    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
