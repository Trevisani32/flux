package com.flux.recorrencias.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.LancamentosDasRecorrencias;
import com.flux.recorrencias.interno.dto.AConfirmarResponse;
import com.flux.recorrencias.interno.dto.ConfirmacaoResponse;
import com.flux.recorrencias.interno.dto.ConfirmarRequest;
import com.flux.recorrencias.interno.dto.CandidatosResponse;
import com.flux.recorrencias.interno.dto.HistoricoResponse;
import com.flux.recorrencias.interno.dto.LigarRequest;
import com.flux.recorrencias.interno.dto.OcorrenciaResponse;
import com.flux.recorrencias.interno.dto.PreviaRequest;
import com.flux.recorrencias.interno.dto.PreviaResponse;
import com.flux.recorrencias.interno.dto.RecorrenciaResponse;
import com.flux.recorrencias.interno.dto.RecorrenciasResponse;
import com.flux.recorrencias.interno.dto.SalvarRecorrenciaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de cadastro, cálculo e ações das recorrências. */
@Service
public class RecorrenciaService {

    private final RecorrenciaRepository recorrencias;
    private final OcorrenciaTratadaRepository tratadas;
    private final ContasConsulta contas;
    private final CartoesConsulta cartoes;
    private final CategoriasConsulta categorias;
    private final LancamentosDasRecorrencias lancamentos;
    private final Clock clock;

    public RecorrenciaService(RecorrenciaRepository recorrencias, OcorrenciaTratadaRepository tratadas,
                              ContasConsulta contas, CartoesConsulta cartoes, CategoriasConsulta categorias,
                              LancamentosDasRecorrencias lancamentos, Clock clock) {
        this.recorrencias = recorrencias;
        this.tratadas = tratadas;
        this.contas = contas;
        this.cartoes = cartoes;
        this.categorias = categorias;
        this.lancamentos = lancamentos;
        this.clock = clock;
    }

    @Transactional
    public RecorrenciaResponse criar(Long usuarioId, SalvarRecorrenciaRequest pedido) {
        validarCampos(pedido);
        travarTudo(usuarioId);
        if (recorrencias.countByUsuarioId(usuarioId) >= 200) {
            throw new RegraDeNegocioException("Você chegou ao limite de 200 recorrências.");
        }
        Recorrencia recorrencia = new Recorrencia(usuarioId, hoje(), agora());
        aplicar(usuarioId, recorrencia, pedido, false);
        return resposta(usuarioId, recorrencias.save(recorrencia));
    }

    @Transactional(readOnly = true)
    public RecorrenciaResponse buscar(Long usuarioId, Long id) {
        return resposta(usuarioId, encontrar(usuarioId, id));
    }

    @Transactional(readOnly = true)
    public RecorrenciasResponse listar(Long usuarioId) {
        List<Recorrencia> todas = recorrencias.findAllByUsuarioId(usuarioId);
        // As visíveis de todas, calculadas uma vez só (e não uma vez por recorrência).
        List<OcorrenciaResponse> visiveis = ocorrenciasVisiveis(usuarioId, CalendarioDaRecorrencia.visivelDesde(hoje()),
                CalendarioDaRecorrencia.visivelAte(hoje()));
        List<RecorrenciaResponse> ativas = new ArrayList<>();
        List<RecorrenciaResponse> pausadas = new ArrayList<>();
        List<RecorrenciaResponse> encerradas = new ArrayList<>();
        for (Recorrencia recorrencia : todas) {
            RecorrenciaResponse resposta = resposta(usuarioId, recorrencia, visiveis);
            switch (resposta.situacao()) {
                case "ATIVA" -> ativas.add(resposta);
                case "PAUSADA" -> pausadas.add(resposta);
                default -> encerradas.add(resposta);
            }
        }
        ativas.sort(Comparator.comparing(RecorrenciaResponse::proxima, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RecorrenciaResponse::descricao, TextoComparavel.ordemAlfabetica())
                .thenComparing(RecorrenciaResponse::id));
        pausadas.sort(Comparator.comparing(RecorrenciaResponse::descricao, TextoComparavel.ordemAlfabetica())
                .thenComparing(RecorrenciaResponse::id));
        encerradas.sort(Comparator.comparing(RecorrenciaResponse::terminaEm, Comparator.reverseOrder())
                .thenComparing(RecorrenciaResponse::descricao, TextoComparavel.ordemAlfabetica())
                .thenComparing(RecorrenciaResponse::id));
        return new RecorrenciasResponse(ativas, pausadas, encerradas, aConfirmar(usuarioId));
    }

    @Transactional
    public RecorrenciaResponse editar(Long usuarioId, Long id, SalvarRecorrenciaRequest pedido) {
        validarCampos(pedido);
        travarTudo(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        if (recorrencia.encerrada(hoje())) {
            throw new RegraDeNegocioException("Retome a recorrência antes de editar.");
        }
        if (recorrencia.getFrequencia() != pedido.frequencia()) {
            throw new CampoInvalidoException("frequencia", "A frequência não muda. Encerre esta recorrência e crie outra.");
        }
        aplicar(usuarioId, recorrencia, pedido, true);
        return resposta(usuarioId, recorrencia);
    }

    @Transactional
    public RecorrenciaResponse encerrar(Long usuarioId, Long id) {
        recorrencias.travarRecorrenciasDoUsuario(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        recorrencia.encerrar(hoje(), agora());
        return resposta(usuarioId, recorrencia);
    }

    @Transactional
    public RecorrenciaResponse retomar(Long usuarioId, Long id) {
        recorrencias.travarRecorrenciasDoUsuario(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        recorrencia.retomar(hoje(), agora());
        return resposta(usuarioId, recorrencia);
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        recorrencias.travarRecorrenciasDoUsuario(usuarioId);
        recorrencias.delete(encontrar(usuarioId, id));
    }

    @Transactional(readOnly = true)
    public AConfirmarResponse aConfirmar(Long usuarioId) {
        LocalDate inicio = CalendarioDaRecorrencia.visivelDesde(hoje());
        LocalDate fim = hoje().plusDays(7);
        List<OcorrenciaResponse> itens = ocorrenciasVisiveis(usuarioId, inicio, fim);
        itens.sort(Comparator.comparing(OcorrenciaResponse::data)
                .thenComparing(OcorrenciaResponse::descricao, TextoComparavel.ordemAlfabetica())
                .thenComparing(OcorrenciaResponse::recorrenciaId));
        return new AConfirmarResponse(fim, recorrencias.countByUsuarioId(usuarioId), itens);
    }

    @Transactional(readOnly = true)
    public PreviaResponse previa(Long usuarioId, PreviaRequest pedido) {
        validarPrevia(pedido);
        LocalDate desde = hoje();
        if (pedido.recorrenciaId() != null) {
            desde = encontrar(usuarioId, pedido.recorrenciaId()).getOcorrenciasDesde();
        }
        if (pedido.cartaoId() != null && cartoes.buscar(usuarioId, pedido.cartaoId()).isEmpty()) {
            throw new RegraDeNegocioException("Cartão não encontrado.");
        }
        CalendarioDaRecorrencia calendario = new CalendarioDaRecorrencia(pedido.frequencia(), pedido.diaDoMes(),
                pedido.comecaEm(), pedido.terminaEm(), desde);
        int quantidade = pedido.frequencia() == Frequencia.ANUAL ? 3 : 5;
        List<CalendarioDaRecorrencia.Ocorrencia> proximas = calendario.proximas(hoje(), quantidade);
        List<LancamentosDasRecorrencias.FaturaDaData> faturas = pedido.cartaoId() == null ? List.of()
                : lancamentos.faturasDe(usuarioId, pedido.cartaoId(), proximas.stream()
                        .map(CalendarioDaRecorrencia.Ocorrencia::data).toList());
        List<PreviaResponse.Item> itens = new ArrayList<>();
        for (int indice = 0; indice < proximas.size(); indice++) {
            CalendarioDaRecorrencia.Ocorrencia ocorrencia = proximas.get(indice);
            LancamentosDasRecorrencias.FaturaDaData fatura = faturas.isEmpty() ? null : faturas.get(indice);
            itens.add(new PreviaResponse.Item(ocorrencia.data(), calendario.ajustada(ocorrencia.data()),
                    fatura == null ? null : fatura.mes(), fatura == null ? null : fatura.vencimento()));
        }
        return new PreviaResponse(desde, pedido.comecaEm().isBefore(desde), itens);
    }

    @Transactional
    public ConfirmacaoResponse confirmar(Long usuarioId, Long id, String periodoTexto, ConfirmarRequest pedido) {
        LocalDate periodo = periodo(periodoTexto);
        travarTudo(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        CalendarioDaRecorrencia.Ocorrencia ocorrencia = acharOcorrencia(recorrencia, periodo);
        OcorrenciaTratada tratada = tratadas.findByUsuarioIdAndRecorrenciaIdAndPeriodo(usuarioId, id, periodo).orElse(null);
        conferirAcao(usuarioId, recorrencia, tratada);
        String observacao = pedido.observacao() == null ? null : TextoComparavel.limparEspacos(pedido.observacao());
        if (pedido.data().isAfter(hoje().plusMonths(12))) {
            String limite = hoje().plusMonths(12).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            throw new CampoInvalidoException("data", "A data pode ir até " + limite + ".");
        }
        if (recorrencia.getContaId() != null) {
            LancamentosDasRecorrencias.Lancado lancado = lancamentos.lancar(usuarioId,
                    new LancamentosDasRecorrencias.NovoLancamento(recorrencia.getContaId(), recorrencia.getTipo().name(),
                            pedido.data(), recorrencia.getDescricao(), pedido.valor(), recorrencia.getCategoriaId(), observacao));
            gravarEfetivada(usuarioId, recorrencia, ocorrencia, lancado.lancamentoId(), null);
            return new ConfirmacaoResponse(id, periodo, lancado.lancamentoId(), null, lancado.data(), null, null);
        }
        LancamentosDasRecorrencias.Comprado comprado = lancamentos.comprar(usuarioId,
                new LancamentosDasRecorrencias.NovaCompra(recorrencia.getCartaoId(), pedido.data(), recorrencia.getDescricao(),
                        pedido.valor(), recorrencia.getCategoriaId(), observacao));
        gravarEfetivada(usuarioId, recorrencia, ocorrencia, null, comprado.compraId());
        String nome = cartoes.buscar(usuarioId, recorrencia.getCartaoId()).map(CartaoResumo::nome).orElse(null);
        return new ConfirmacaoResponse(id, periodo, null, comprado.compraId(), comprado.data(), comprado.fatura(), nome);
    }

    @Transactional
    public ConfirmacaoResponse ligar(Long usuarioId, Long id, String periodoTexto, LigarRequest pedido) {
        LocalDate periodo = periodo(periodoTexto);
        if (pedido.lancamentoId() != null && pedido.compraId() != null) {
            throw new CampoInvalidoException("lancamentoId", "Escolha um lançamento ou uma compra.");
        }
        travarTudo(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        CalendarioDaRecorrencia.Ocorrencia ocorrencia = acharOcorrencia(recorrencia, periodo);
        OcorrenciaTratada tratada = tratadas.findByUsuarioIdAndRecorrenciaIdAndPeriodo(usuarioId, id, periodo).orElse(null);
        conferirAcao(usuarioId, recorrencia, tratada);
        if (recorrencia.getContaId() != null) {
            if (pedido.lancamentoId() == null || pedido.compraId() != null) {
                throw new RegraDeNegocioException("Escolha um lançamento desta conta e do mesmo tipo.");
            }
            LancamentosDasRecorrencias.Alvo alvo = lancamentos.lancamentos(usuarioId, List.of(pedido.lancamentoId())).stream()
                    .findFirst().orElseThrow(() -> new RegraDeNegocioException("Lançamento não encontrado."));
            if (!recorrencia.getContaId().equals(alvo.contaId()) || !recorrencia.getTipo().name().equals(alvo.tipo()) || alvo.ignorado()) {
                throw new RegraDeNegocioException("Escolha um lançamento desta conta e do mesmo tipo.");
            }
            conferirOutraLigacao(usuarioId, tratadas.findByUsuarioIdAndLancamentoId(usuarioId, alvo.id()).orElse(null), alvo, false);
            gravarEfetivada(usuarioId, recorrencia, ocorrencia, alvo.id(), null);
            return new ConfirmacaoResponse(id, periodo, alvo.id(), null, alvo.data(), null, null);
        }
        if (pedido.compraId() == null || pedido.lancamentoId() != null) {
            throw new RegraDeNegocioException("Escolha uma compra deste cartão.");
        }
        LancamentosDasRecorrencias.Alvo alvo = lancamentos.compras(usuarioId, List.of(pedido.compraId())).stream().findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("Compra não encontrada."));
        if (!recorrencia.getCartaoId().equals(alvo.cartaoId()) || !"COMPRA".equals(alvo.tipo())) {
            throw new RegraDeNegocioException("Escolha uma compra deste cartão.");
        }
        conferirOutraLigacao(usuarioId, tratadas.findByUsuarioIdAndCompraId(usuarioId, alvo.id()).orElse(null), alvo, true);
        gravarEfetivada(usuarioId, recorrencia, ocorrencia, null, alvo.id());
        return new ConfirmacaoResponse(id, periodo, null, alvo.id(), alvo.data(), null, null);
    }

    @Transactional(readOnly = true)
    public CandidatosResponse candidatos(Long usuarioId, Long id, String periodoTexto) {
        LocalDate periodo = periodo(periodoTexto);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        CalendarioDaRecorrencia.Ocorrencia ocorrencia = acharOcorrencia(recorrencia, periodo);
        LocalDate de = ocorrencia.data().minusDays(10);
        LocalDate ate = ocorrencia.data().plusDays(10);
        List<LancamentosDasRecorrencias.Alvo> alvos = recorrencia.getContaId() != null
                ? lancamentos.lancamentosDaConta(usuarioId, recorrencia.getContaId(), de, ate)
                : lancamentos.comprasDoCartao(usuarioId, recorrencia.getCartaoId(), de, ate);
        List<CandidatosResponse.Candidato> candidatos = alvos.stream().filter(alvo -> !alvo.ignorado())
                .filter(alvo -> recorrencia.getContaId() == null || recorrencia.getTipo().name().equals(alvo.tipo()))
                .filter(alvo -> !ligadoAOutraOcorrencia(usuarioId, alvo, recorrencia.getContaId() == null))
                .sorted(Comparator.comparingLong((LancamentosDasRecorrencias.Alvo alvo) -> Math.abs(alvo.data().toEpochDay()
                        - ocorrencia.data().toEpochDay())).thenComparing(alvo -> alvo.valor().abs().subtract(recorrencia.getValor()).abs())
                        .thenComparing(LancamentosDasRecorrencias.Alvo::id)).map(alvo -> candidato(usuarioId, alvo)).toList();
        CandidatosResponse.Candidato sugerido = candidatos.stream().filter(candidato -> sugerido(candidato, recorrencia, ocorrencia.data()))
                .findFirst().orElse(null);
        return new CandidatosResponse(de, ate, sugerido, candidatos);
    }

    @Transactional(readOnly = true)
    public HistoricoResponse historico(Long usuarioId, Long id) {
        Recorrencia recorrencia = encontrar(usuarioId, id);
        List<HistoricoResponse.Item> itens = new ArrayList<>();
        List<OcorrenciaTratada> daRecorrencia = tratadas.findAllByUsuarioIdAndRecorrenciaId(usuarioId, id);
        Map<Long, LancamentosDasRecorrencias.Alvo> porLancamento = alvosPorLancamento(usuarioId, daRecorrencia);
        Map<Long, LancamentosDasRecorrencias.Alvo> porCompra = alvosPorCompra(usuarioId, daRecorrencia);
        for (OcorrenciaTratada tratada : daRecorrencia) {
            if (tratada.getSituacao() == OcorrenciaTratada.Situacao.PULADA) {
                itens.add(new HistoricoResponse.Item(tratada.getPeriodo(), tratada.getDataPrevista(), "PULADA", null, null,
                        null, null, null, recorrencia.getContaId(), recorrencia.getCartaoId()));
            } else if (tratadaValida(usuarioId, tratada)) {
                LancamentosDasRecorrencias.Alvo alvo = tratada.getLancamentoId() == null ? porCompra.get(tratada.getCompraId())
                        : porLancamento.get(tratada.getLancamentoId());
                if (alvo != null) {
                    itens.add(new HistoricoResponse.Item(tratada.getPeriodo(), tratada.getDataPrevista(), "EFETIVADA", alvo.valor(),
                            alvo.data(), tratada.getLancamentoId(), tratada.getCompraId(), alvo.mesNaLista(), alvo.contaId(),
                            alvo.cartaoId()));
                }
            }
        }
        // Seção 2.2: as atrasadas e só a próxima prevista; as outras previstas ficam em Lançamentos.
        List<OcorrenciaResponse> visiveis = ocorrenciasVisiveis(usuarioId, CalendarioDaRecorrencia.visivelDesde(hoje()),
                CalendarioDaRecorrencia.visivelAte(hoje())).stream().filter(visivel -> visivel.recorrenciaId().equals(id)).toList();
        List<OcorrenciaResponse> doHistorico = new ArrayList<>(visiveis.stream().filter(OcorrenciaResponse::atrasada).toList());
        visiveis.stream().filter(visivel -> !visivel.atrasada()).min(Comparator.comparing(OcorrenciaResponse::data))
                .ifPresent(doHistorico::add);
        for (OcorrenciaResponse visivel : doHistorico) {
            itens.add(new HistoricoResponse.Item(visivel.periodo(), visivel.data(), visivel.atrasada() ? "ATRASADA" : "PREVISTA",
                    visivel.valor(), null, null, null, null, recorrencia.getContaId(), recorrencia.getCartaoId()));
        }
        itens.sort(Comparator.comparing(HistoricoResponse.Item::dataPrevista).reversed());
        LocalDate desde = LocalDate.ofInstant(recorrencia.getCriadaEm(), clock.getZone());
        return new HistoricoResponse(id, recorrencia.getDescricao(), recorrencia.getFrequencia().name(), desde, itens);
    }

    @Transactional
    public OcorrenciaResponse pular(Long usuarioId, Long id, String periodoTexto) {
        LocalDate periodo = periodo(periodoTexto);
        recorrencias.travarRecorrenciasDoUsuario(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        CalendarioDaRecorrencia.Ocorrencia ocorrencia = acharOcorrencia(recorrencia, periodo);
        OcorrenciaTratada tratada = tratadas.findByUsuarioIdAndRecorrenciaIdAndPeriodo(usuarioId, id, periodo).orElse(null);
        if (tratada != null && tratada.getSituacao() == OcorrenciaTratada.Situacao.EFETIVADA && tratadaValida(usuarioId, tratada)) {
            throw new RegraDeNegocioException("Esta ocorrência já foi confirmada.");
        }
        if (tratada == null) {
            tratada = new OcorrenciaTratada(usuarioId, id, periodo);
        }
        tratada.pular(ocorrencia.data(), agora());
        tratadas.save(tratada);
        return paraOcorrencia(usuarioId, recorrencia, ocorrencia);
    }

    @Transactional
    public void voltarAPrever(Long usuarioId, Long id, String periodoTexto) {
        LocalDate periodo = periodo(periodoTexto);
        recorrencias.travarRecorrenciasDoUsuario(usuarioId);
        Recorrencia recorrencia = encontrar(usuarioId, id);
        acharOcorrencia(recorrencia, periodo);
        Optional<OcorrenciaTratada> encontrada = tratadas.findByUsuarioIdAndRecorrenciaIdAndPeriodo(usuarioId, id, periodo);
        if (encontrada.isPresent() && encontrada.get().getSituacao() == OcorrenciaTratada.Situacao.EFETIVADA
                && tratadaValida(usuarioId, encontrada.get())) {
            throw new RegraDeNegocioException("Esta ocorrência já foi confirmada.");
        }
        encontrada.filter(item -> item.getSituacao() == OcorrenciaTratada.Situacao.PULADA).ifPresent(tratadas::delete);
    }

    List<OcorrenciaResponse> ocorrenciasVisiveis(Long usuarioId, LocalDate de, LocalDate ate) {
        LocalDate inicio = de.isAfter(CalendarioDaRecorrencia.visivelDesde(hoje())) ? de
                : CalendarioDaRecorrencia.visivelDesde(hoje());
        LocalDate fim = ate.isBefore(CalendarioDaRecorrencia.visivelAte(hoje())) ? ate
                : CalendarioDaRecorrencia.visivelAte(hoje());
        if (inicio.isAfter(fim)) {
            return List.of();
        }
        List<OcorrenciaTratada> daJanela = tratadas.findAllByUsuarioIdAndPeriodoBetween(usuarioId, inicio.minusYears(1), fim);
        Set<Long> naoIgnorados = lancamentosNaoIgnorados(usuarioId, daJanela);
        Set<String> tratadasValidas = new HashSet<>();
        for (OcorrenciaTratada tratada : daJanela) {
            boolean valida = tratada.getSituacao() == OcorrenciaTratada.Situacao.PULADA || tratada.getCompraId() != null
                    || tratada.getLancamentoId() != null && naoIgnorados.contains(tratada.getLancamentoId());
            if (valida) {
                tratadasValidas.add(tratada.getRecorrenciaId() + ":" + tratada.getPeriodo());
            }
        }
        Map<Long, ContaResumo> contasPorId = porId(contas.listar(usuarioId), ContaResumo::id);
        Map<Long, CartaoResumo> cartoesPorId = porId(cartoes.listar(usuarioId), CartaoResumo::id);
        Map<Long, CategoriaResumo> categoriasPorId = porId(categorias.listar(usuarioId), CategoriaResumo::id);
        List<OcorrenciaResponse> resultado = new ArrayList<>();
        for (Recorrencia recorrencia : recorrencias.findAllByUsuarioId(usuarioId)) {
            Nomes nomes = new Nomes(categoriasPorId.get(recorrencia.getCategoriaId()), contasPorId.get(recorrencia.getContaId()),
                    cartoesPorId.get(recorrencia.getCartaoId()));
            if (motivoDaPausa(nomes).isPresent()) {
                continue;
            }
            for (CalendarioDaRecorrencia.Ocorrencia ocorrencia : recorrencia.calendario().entre(inicio, fim)) {
                if (!tratadasValidas.contains(recorrencia.getId() + ":" + ocorrencia.periodo())) {
                    resultado.add(paraOcorrencia(recorrencia, nomes, ocorrencia));
                }
            }
        }
        return resultado;
    }

    private CandidatosResponse.Candidato candidato(Long usuarioId, LancamentosDasRecorrencias.Alvo alvo) {
        CategoriaResumo categoria = alvo.categoriaId() == null ? null : categorias.buscar(usuarioId, alvo.categoriaId()).orElse(null);
        return new CandidatosResponse.Candidato(alvo.id(), alvo.contaId() == null ? "COMPRA" : "LANCAMENTO", alvo.data(),
                alvo.descricao(), alvo.valor(), alvo.origem(), categoria == null ? null : categoria.nome(),
                categoria == null ? null : categoria.nomeDaMae());
    }

    private boolean sugerido(CandidatosResponse.Candidato candidato, Recorrencia recorrencia, LocalDate dataPrevista) {
        long dias = Math.abs(candidato.data().toEpochDay() - dataPrevista.toEpochDay());
        BigDecimal percentual = recorrencia.isValorVaria() ? new BigDecimal("0.50") : new BigDecimal("0.10");
        return dias <= 3 && candidato.valor().abs().subtract(recorrencia.getValor()).abs()
                .compareTo(recorrencia.getValor().multiply(percentual)) <= 0;
    }

    private boolean ligadoAOutraOcorrencia(Long usuarioId, LancamentosDasRecorrencias.Alvo alvo, boolean compra) {
        Optional<OcorrenciaTratada> ligada = compra ? tratadas.findByUsuarioIdAndCompraId(usuarioId, alvo.id())
                : tratadas.findByUsuarioIdAndLancamentoId(usuarioId, alvo.id());
        return ligada.filter(item -> tratadaValida(usuarioId, item)).isPresent();
    }

    private void conferirOutraLigacao(Long usuarioId, OcorrenciaTratada outra, LancamentosDasRecorrencias.Alvo alvo, boolean compra) {
        if (outra == null || !tratadaValida(usuarioId, outra)) {
            return;
        }
        Recorrencia recorrencia = encontrar(usuarioId, outra.getRecorrenciaId());
        String frase = compra ? "Esta compra já está ligada a " : "Este lançamento já está ligado a ";
        throw new RegraDeNegocioException(frase + recorrencia.getDescricao() + " " + nomeDoPeriodo(recorrencia, outra.getPeriodo()) + ".");
    }

    private String nomeDoPeriodo(Recorrencia recorrencia, LocalDate periodo) {
        if (recorrencia.getFrequencia() == Frequencia.ANUAL) {
            return "de " + periodo.getYear();
        }
        if (recorrencia.getFrequencia() == Frequencia.SEMANAL) {
            String ano = periodo.getYear() == hoje().getYear() ? "" : "/" + periodo.getYear();
            return "da semana de " + periodo.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + ano;
        }
        String mes = periodo.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("pt-BR"));
        return "de " + mes + (periodo.getYear() == hoje().getYear() ? "" : " de " + periodo.getYear());
    }

    private Map<Long, LancamentosDasRecorrencias.Alvo> alvosPorLancamento(Long usuarioId, List<OcorrenciaTratada> ocorrencias) {
        List<Long> ids = ocorrencias.stream().map(OcorrenciaTratada::getLancamentoId).filter(java.util.Objects::nonNull).toList();
        Map<Long, LancamentosDasRecorrencias.Alvo> resultado = new HashMap<>();
        lancamentos.lancamentos(usuarioId, ids).forEach(alvo -> resultado.put(alvo.id(), alvo));
        return resultado;
    }

    private Map<Long, LancamentosDasRecorrencias.Alvo> alvosPorCompra(Long usuarioId, List<OcorrenciaTratada> ocorrencias) {
        List<Long> ids = ocorrencias.stream().map(OcorrenciaTratada::getCompraId).filter(java.util.Objects::nonNull).toList();
        Map<Long, LancamentosDasRecorrencias.Alvo> resultado = new HashMap<>();
        lancamentos.compras(usuarioId, ids).forEach(alvo -> resultado.put(alvo.id(), alvo));
        return resultado;
    }

    Map<Long, Long> ligacoesPorLancamento(Long usuarioId, java.util.Collection<Long> ids) {
        Map<Long, Long> resultado = new HashMap<>();
        for (OcorrenciaTratada tratada : tratadas.findAllByUsuarioIdAndLancamentoIdIn(usuarioId, ids)) {
            if (tratada.getSituacao() == OcorrenciaTratada.Situacao.EFETIVADA && tratadaValida(usuarioId, tratada)) {
                resultado.put(tratada.getLancamentoId(), tratada.getRecorrenciaId());
            }
        }
        return resultado;
    }

    Map<Long, Long> ligacoesPorCompra(Long usuarioId, java.util.Collection<Long> ids) {
        Map<Long, Long> resultado = new HashMap<>();
        for (OcorrenciaTratada tratada : tratadas.findAllByUsuarioIdAndCompraIdIn(usuarioId, ids)) {
            if (tratada.getSituacao() == OcorrenciaTratada.Situacao.EFETIVADA && tratadaValida(usuarioId, tratada)) {
                resultado.put(tratada.getCompraId(), tratada.getRecorrenciaId());
            }
        }
        return resultado;
    }

    private void aplicar(Long usuarioId, Recorrencia recorrencia, SalvarRecorrenciaRequest pedido, boolean edicao) {
        verificarAlvos(usuarioId, recorrencia, pedido, edicao);
        recorrencia.alterar(TextoComparavel.limparEspacos(pedido.descricao()), pedido.valor().setScale(2, RoundingMode.UNNECESSARY),
                pedido.tipo(), pedido.categoriaId(), pedido.contaId(), pedido.cartaoId(), pedido.frequencia(), pedido.diaDoMes(),
                pedido.comecaEm(), pedido.terminaEm(), Boolean.TRUE.equals(pedido.valorVaria()), agora());
    }

    private void verificarAlvos(Long usuarioId, Recorrencia atual, SalvarRecorrenciaRequest pedido, boolean edicao) {
        if (pedido.cartaoId() != null && pedido.tipo() == TipoDaRecorrencia.RECEITA) {
            throw new RegraDeNegocioException("O cartão só recebe despesa: escolha uma conta para a receita.");
        }
        if (pedido.contaId() != null) {
            ContaResumo conta = contas.buscar(usuarioId, pedido.contaId())
                    .orElseThrow(() -> new RegraDeNegocioException("Conta não encontrada."));
            if (conta.arquivada() && (!edicao || !pedido.contaId().equals(atual.getContaId()))) {
                throw new RegraDeNegocioException(conta.nome() + " está arquivada. Escolha uma conta ativa.");
            }
        }
        if (pedido.cartaoId() != null) {
            CartaoResumo cartao = cartoes.buscar(usuarioId, pedido.cartaoId())
                    .orElseThrow(() -> new RegraDeNegocioException("Cartão não encontrado."));
            if (cartao.arquivado() && (!edicao || !pedido.cartaoId().equals(atual.getCartaoId()))) {
                throw new RegraDeNegocioException(cartao.nome() + " está arquivado. Escolha um cartão ativo.");
            }
        }
        if (pedido.categoriaId() != null) {
            CategoriaResumo categoria = categorias.buscar(usuarioId, pedido.categoriaId())
                    .orElseThrow(() -> new RegraDeNegocioException("Categoria não encontrada."));
            if (!categoria.natureza().equals(pedido.tipo().name())) {
                throw new RegraDeNegocioException("Escolha uma categoria de "
                        + (pedido.tipo() == TipoDaRecorrencia.RECEITA ? "receita." : "despesa."));
            }
            if (categoria.arquivada() && (!edicao || !pedido.categoriaId().equals(atual.getCategoriaId()))) {
                throw new RegraDeNegocioException("Escolha uma categoria ativa.");
            }
        }
    }

    private void validarCampos(SalvarRecorrenciaRequest pedido) {
        if (pedido.frequencia() == Frequencia.MENSAL && (pedido.diaDoMes() == null || pedido.diaDoMes() < 1 || pedido.diaDoMes() > 31)) {
            throw new CampoInvalidoException("diaDoMes", "Escolha um dia de 1 a 31.");
        }
        if (pedido.comecaEm() != null && pedido.comecaEm().isAfter(hoje().plusMonths(12))) {
            throw new CampoInvalidoException("comecaEm", "O início pode ir até "
                    + hoje().plusMonths(12).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        if (pedido.terminaEm() != null && pedido.comecaEm() != null && pedido.terminaEm().isBefore(pedido.comecaEm())) {
            throw new CampoInvalidoException("terminaEm", "O fim precisa ser no dia do início ou depois ("
                    + pedido.comecaEm().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").");
        }
        if (pedido.contaId() == null && pedido.cartaoId() == null) {
            throw new CampoInvalidoException("contaId", "Escolha a conta ou o cartão.");
        }
        if (pedido.contaId() != null && pedido.cartaoId() != null) {
            throw new CampoInvalidoException("contaId", "Escolha a conta ou o cartão, não os dois.");
        }
    }

    private void validarPrevia(PreviaRequest pedido) {
        if (pedido.frequencia() == Frequencia.MENSAL && (pedido.diaDoMes() == null || pedido.diaDoMes() < 1 || pedido.diaDoMes() > 31)) {
            throw new CampoInvalidoException("diaDoMes", "Escolha um dia de 1 a 31.");
        }
        if (pedido.terminaEm() != null && pedido.comecaEm() != null && pedido.terminaEm().isBefore(pedido.comecaEm())) {
            throw new CampoInvalidoException("terminaEm", "O fim precisa ser no dia do início ou depois ("
                    + pedido.comecaEm().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").");
        }
        if (pedido.comecaEm() != null && pedido.comecaEm().isAfter(hoje().plusMonths(12))) {
            throw new CampoInvalidoException("comecaEm", "O início pode ir até "
                    + hoje().plusMonths(12).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
    }

    private void travarTudo(Long usuarioId) {
        contas.travar(usuarioId);
        cartoes.travar(usuarioId);
        categorias.travar(usuarioId);
        recorrencias.travarRecorrenciasDoUsuario(usuarioId);
    }

    private Recorrencia encontrar(Long usuarioId, Long id) {
        return recorrencias.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada."));
    }

    private LocalDate periodo(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (RuntimeException exception) {
            throw new CampoInvalidoException("periodo", "Período inválido.");
        }
    }

    private CalendarioDaRecorrencia.Ocorrencia acharOcorrencia(Recorrencia recorrencia, LocalDate periodo) {
        if (!CalendarioDaRecorrencia.abrePeriodo(recorrencia.getFrequencia(), periodo)) {
            throw new RecursoNaoEncontradoException("Ocorrência não encontrada.");
        }
        return recorrencia.calendario().doPeriodo(periodo)
                .filter(item -> !item.data().isBefore(CalendarioDaRecorrencia.visivelDesde(hoje()))
                        && !item.data().isAfter(CalendarioDaRecorrencia.visivelAte(hoje())))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
    }

    private void conferirAcao(Long usuarioId, Recorrencia recorrencia, OcorrenciaTratada tratada) {
        if (tratada != null && tratada.getSituacao() == OcorrenciaTratada.Situacao.EFETIVADA && tratadaValida(usuarioId, tratada)) {
            throw new RegraDeNegocioException("Esta ocorrência já foi confirmada.");
        }
        if (tratada != null && tratada.getSituacao() == OcorrenciaTratada.Situacao.PULADA) {
            throw new RegraDeNegocioException("Esta ocorrência foi pulada. Desfaça antes de confirmar.");
        }
        pausada(recorrencia).ifPresent(motivo -> {
            throw new RegraDeNegocioException("A recorrência está pausada: " + motivo);
        });
    }

    boolean tratadaValida(Long usuarioId, OcorrenciaTratada tratada) {
        if (tratada.getSituacao() == OcorrenciaTratada.Situacao.PULADA || tratada.getCompraId() != null) {
            return true;
        }
        if (tratada.getLancamentoId() == null) {
            return false;
        }
        return lancamentos.lancamentos(usuarioId, List.of(tratada.getLancamentoId())).stream().anyMatch(alvo -> !alvo.ignorado());
    }

    private void gravarEfetivada(Long usuarioId, Recorrencia recorrencia, CalendarioDaRecorrencia.Ocorrencia ocorrencia,
                                 Long lancamentoId, Long compraId) {
        OcorrenciaTratada tratada = tratadas.findByUsuarioIdAndRecorrenciaIdAndPeriodo(usuarioId, recorrencia.getId(),
                ocorrencia.periodo()).orElse(new OcorrenciaTratada(usuarioId, recorrencia.getId(), ocorrencia.periodo()));
        tratada.efetivar(ocorrencia.data(), lancamentoId, compraId, agora());
        tratadas.save(tratada);
    }

    private RecorrenciaResponse resposta(Long usuarioId, Recorrencia recorrencia) {
        return resposta(usuarioId, recorrencia, pausada(recorrencia).isPresent() ? List.of()
                : ocorrenciasVisiveis(usuarioId, CalendarioDaRecorrencia.visivelDesde(hoje()), CalendarioDaRecorrencia.visivelAte(hoje())));
    }

    /** {@code visiveisDoUsuario}: as visíveis de todas as recorrências do usuário; aqui ficam só as desta. */
    private RecorrenciaResponse resposta(Long usuarioId, Recorrencia recorrencia, List<OcorrenciaResponse> visiveisDoUsuario) {
        Optional<String> pausa = pausada(recorrencia);
        LocalDate hoje = hoje();
        List<OcorrenciaResponse> visiveis = pausa.isPresent() ? List.of()
                : visiveisDoUsuario.stream().filter(item -> item.recorrenciaId().equals(recorrencia.getId())).toList();
        String situacao = recorrencia.encerrada(hoje) ? "ENCERRADA" : pausa.isPresent() ? "PAUSADA" : "ATIVA";
        LocalDate proxima = "ATIVA".equals(situacao) ? visiveis.stream().filter(item -> !item.data().isBefore(hoje))
                .map(OcorrenciaResponse::data).min(Comparator.naturalOrder()).orElse(null) : null;
        CategoriaResumo categoria = recorrencia.getCategoriaId() == null ? null
                : categorias.buscar(usuarioId, recorrencia.getCategoriaId()).orElse(null);
        ContaResumo conta = recorrencia.getContaId() == null ? null : contas.buscar(usuarioId, recorrencia.getContaId()).orElse(null);
        CartaoResumo cartao = recorrencia.getCartaoId() == null ? null : cartoes.buscar(usuarioId, recorrencia.getCartaoId()).orElse(null);
        int confirmadas = (int) tratadas.findAllByUsuarioIdAndRecorrenciaId(usuarioId, recorrencia.getId()).stream()
                .filter(item -> item.getSituacao() == OcorrenciaTratada.Situacao.EFETIVADA)
                .filter(item -> tratadaValida(usuarioId, item)).count();
        return new RecorrenciaResponse(recorrencia.getId(), recorrencia.getDescricao(), recorrencia.getTipo().name(),
                recorrencia.getValor(), recorrencia.valorComSinal(), recorrencia.isValorVaria(), recorrencia.getCategoriaId(),
                categoria == null ? null : categoria.nome(), categoria == null ? null : categoria.nomeDaMae(),
                categoria != null && categoria.arquivada(), recorrencia.getContaId(), conta == null ? null : conta.nome(),
                recorrencia.getCartaoId(), cartao == null ? null : cartao.nome(), cartao == null ? null : cartao.cor(),
                recorrencia.getFrequencia().name(), recorrencia.getDiaDoMes(), recorrencia.getComecaEm(), recorrencia.getTerminaEm(),
                recorrencia.getOcorrenciasDesde(), situacao, pausa.orElse(null), proxima,
                (int) visiveis.stream().filter(item -> item.atrasada()).count(), confirmadas);
    }

    /** A categoria, a conta e o cartão de uma recorrência, lidos uma vez para todas as ocorrências dela. */
    private record Nomes(CategoriaResumo categoria, ContaResumo conta, CartaoResumo cartao) {
    }

    private Nomes nomes(Long usuarioId, Recorrencia recorrencia) {
        return new Nomes(
                recorrencia.getCategoriaId() == null ? null : categorias.buscar(usuarioId, recorrencia.getCategoriaId()).orElse(null),
                recorrencia.getContaId() == null ? null : contas.buscar(usuarioId, recorrencia.getContaId()).orElse(null),
                recorrencia.getCartaoId() == null ? null : cartoes.buscar(usuarioId, recorrencia.getCartaoId()).orElse(null));
    }

    private Set<Long> lancamentosNaoIgnorados(Long usuarioId, List<OcorrenciaTratada> ocorrencias) {
        List<Long> ids = ocorrencias.stream().map(OcorrenciaTratada::getLancamentoId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Set<Long> resultado = new HashSet<>();
        for (int inicio = 0; inicio < ids.size(); inicio += 1_000) {
            int fim = Math.min(inicio + 1_000, ids.size());
            lancamentos.lancamentos(usuarioId, ids.subList(inicio, fim)).stream().filter(alvo -> !alvo.ignorado())
                    .forEach(alvo -> resultado.add(alvo.id()));
        }
        return resultado;
    }

    private static <T> Map<Long, T> porId(List<T> itens, java.util.function.Function<T, Long> id) {
        Map<Long, T> resultado = new HashMap<>();
        itens.forEach(item -> resultado.put(id.apply(item), item));
        return resultado;
    }

    private OcorrenciaResponse paraOcorrencia(Long usuarioId, Recorrencia recorrencia,
                                              CalendarioDaRecorrencia.Ocorrencia ocorrencia) {
        return paraOcorrencia(recorrencia, nomes(usuarioId, recorrencia), ocorrencia);
    }

    private OcorrenciaResponse paraOcorrencia(Recorrencia recorrencia, Nomes nomes, CalendarioDaRecorrencia.Ocorrencia ocorrencia) {
        CategoriaResumo categoria = nomes.categoria();
        ContaResumo conta = nomes.conta();
        CartaoResumo cartao = nomes.cartao();
        boolean atrasada = CalendarioDaRecorrencia.atrasada(ocorrencia.data(), hoje());
        return new OcorrenciaResponse(recorrencia.getId(), ocorrencia.periodo(), ocorrencia.data(), null, recorrencia.getDescricao(),
                recorrencia.getTipo().name(), recorrencia.valorComSinal(), recorrencia.getValor(), recorrencia.isValorVaria(),
                recorrencia.getContaId(), conta == null ? null : conta.nome(), recorrencia.getCartaoId(),
                cartao == null ? null : cartao.nome(), cartao == null ? null : cartao.cor(), recorrencia.getCategoriaId(),
                categoria == null ? null : categoria.nome(), categoria == null ? null : categoria.nomeDaMae(), atrasada,
                atrasada ? ocorrencia.data().plusDays(CalendarioDaRecorrencia.DIAS_DA_ATRASADA) : null);
    }

    private Optional<String> pausada(Recorrencia recorrencia) {
        return motivoDaPausa(nomes(recorrencia.getUsuarioId(), recorrencia));
    }

    private static Optional<String> motivoDaPausa(Nomes nomes) {
        if (nomes.conta() != null && nomes.conta().arquivada()) {
            return Optional.of(nomes.conta().nome() + " está arquivada.");
        }
        if (nomes.cartao() != null && nomes.cartao().arquivado()) {
            return Optional.of(nomes.cartao().nome() + " está arquivado.");
        }
        if (nomes.categoria() != null && nomes.categoria().arquivada()) {
            return Optional.of("a categoria " + nomes.categoria().nome() + " está arquivada.");
        }
        return Optional.empty();
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private Instant agora() {
        return Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
