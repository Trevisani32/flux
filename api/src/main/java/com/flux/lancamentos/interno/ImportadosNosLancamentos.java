package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.lancamentos.LancamentosImportados;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A API da importação sobre a tabela de lançamentos (ADR 0014); a conversão em transferência fica no ConversaoService (ADR 0016). */
@Component
public class ImportadosNosLancamentos implements LancamentosImportados {

    private static final DateTimeFormatter DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final LancamentoRepository repositorio;
    private final RegrasDosLancamentos regras;
    private final CategoriasConsulta categorias;
    private final ConversaoService conversoes;
    private final PagamentoService pagamentos;
    private final ImportacaoNaFatura faturas;

    public ImportadosNosLancamentos(LancamentoRepository repositorio, RegrasDosLancamentos regras, CategoriasConsulta categorias,
                                    ConversaoService conversoes, PagamentoService pagamentos, ImportacaoNaFatura faturas) {
        this.repositorio = repositorio;
        this.regras = regras;
        this.categorias = categorias;
        this.conversoes = conversoes;
        this.pagamentos = pagamentos;
        this.faturas = faturas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Existente> existentes(Long usuarioId, Long contaId, LocalDate inicio, LocalDate fim, Collection<String> chaves) {
        // "in ()" vazio não é SQL válido: sem chave, uma que nunca existe.
        Collection<String> procuradas = chaves.isEmpty() ? List.of("-") : chaves;
        return repositorio.existentesParaImportar(usuarioId, contaId, inicio, fim, procuradas).stream()
                .map(lancamento -> new Existente(lancamento.getId(), lancamento.getData(), lancamento.getValor(),
                        lancamento.getDescricao(), lancamento.getDescricaoBanco(), lancamento.getChaveImportacao(),
                        lancamento.getImportacaoId(), lancamento.getCriadoEm(), lancamento.getIgnoradoEm()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void gravar(Long usuarioId, Long contaId, Long importacaoId, List<Novo> novos) {
        regras.travarContasECategorias(usuarioId);
        regras.exigirLimitesDaImportacao(usuarioId, novos.stream().map(Novo::data).toList());
        Map<Long, CategoriaResumo> doUsuario = categorias.listar(usuarioId).stream()
                .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        Instant agora = regras.agora();
        List<Lancamento> lancamentos = novos.stream().map(novo -> {
            exigirCategoria(doUsuario, novo);
            return Lancamento.importado(usuarioId, contaId, importacaoId, novo.data(), novo.valor(), novo.descricao(),
                    novo.descricaoBanco(), novo.chave(), novo.categoriaId(), agora);
        }).toList();
        repositorio.saveAll(lancamentos);
    }

    /**
     * Primeiro as conversões (decisão 34 da 04c), depois os lançamentos: o DELETE em massa não passa pelo vínculo. O
     * pagamento convertido de uma despesa da importação (Fatia 05b) sai junto, se o cartão dele está ativo.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int desfazer(Long usuarioId, Long importacaoId) {
        pagamentos.desfazerDaImportacao(usuarioId, importacaoId);
        conversoes.desfazerDaImportacao(usuarioId, importacaoId);
        return repositorio.apagarDaImportacao(usuarioId, importacaoId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contagem> contagens(Long usuarioId) {
        List<Contagem> contagens = new ArrayList<>(repositorio.quantidadesDasImportacoes(usuarioId).stream()
                .map(quantidades -> new Contagem(quantidades.importacaoId(), quantidades.editados(), quantidades.excluidos(), 0))
                .toList());
        Map<Long, List<CompraDaImportacao>> porImportacao = repositorio.comprasDasImportacoes(usuarioId).stream()
                .collect(Collectors.groupingBy(CompraDaImportacao::importacaoId, LinkedHashMap::new, Collectors.toList()));
        porImportacao.forEach((importacaoId, compras) -> contagens.add(new Contagem(importacaoId,
                compras.stream().mapToLong(CompraDaImportacao::editada).sum(), 0, compras.size())));
        return contagens;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Excluido> excluidos(Long usuarioId, Long importacaoId) {
        return repositorio.findAllByUsuarioIdAndImportacaoIdAndIgnoradoEmIsNotNullOrderByIgnoradoEmDescIdDesc(usuarioId, importacaoId)
                .stream()
                .map(lancamento -> new Excluido(lancamento.getId(), lancamento.getData(), lancamento.getDescricao(),
                        lancamento.getValor(), lancamento.getIgnoradoEm()))
                .toList();
    }

    @Override
    public List<ParSugerido> paresSugeridos(Long usuarioId, Long importacaoId) {
        return conversoes.paresSugeridos(usuarioId, importacaoId);
    }

    @Override
    public List<Convertido> convertidos(Long usuarioId) {
        return conversoes.convertidos(usuarioId);
    }

    @Override
    public List<PagamentoConvertido> pagamentosConvertidos(Long usuarioId) {
        return pagamentos.convertidosDasImportacoes(usuarioId);
    }

    @Override
    public List<FaturaParaImportar> faturasParaImportar(Long usuarioId, Long cartaoId, LocalDate data) {
        return faturas.faturasParaImportar(usuarioId, cartaoId, data);
    }

    @Override
    public List<ExistenteNoCartao> existentesNoCartao(Long usuarioId, Long cartaoId, YearMonth fatura, LocalDate inicio, LocalDate fim,
                                                       Collection<String> chaves) {
        return faturas.existentes(usuarioId, cartaoId, fatura, inicio, fim, chaves);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long faturaParaImportar(Long usuarioId, Long cartaoId, YearMonth mes) {
        return faturas.faturaParaImportar(usuarioId, cartaoId, mes);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int gravarNaFatura(Long usuarioId, Long cartaoId, YearMonth fatura, Long importacaoId, List<NovaNaFatura> novas) {
        return faturas.gravar(usuarioId, cartaoId, fatura, importacaoId, novas);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int corrigirParcelas(Long usuarioId, List<ValorDoBanco> valores) {
        return faturas.corrigir(usuarioId, valores);
    }

    @Override
    public Map<Long, YearMonth> mesesDasFaturas(Long usuarioId, Collection<Long> faturaIds) {
        return faturas.meses(usuarioId, faturaIds);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int desfazerDaFatura(Long usuarioId, Long importacaoId) {
        return faturas.apagarCompras(usuarioId, importacaoId);
    }

    /**
     * A categoria da linha: do usuário, da natureza do sinal e ativa. Arquivada ou excluída
     * depois da revisão, a importação inteira volta com o 422 e o número da linha.
     */
    private static void exigirCategoria(Map<Long, CategoriaResumo> doUsuario, Novo novo) {
        if (novo.categoriaId() == null) {
            return;
        }
        CategoriaResumo categoria = doUsuario.get(novo.categoriaId());
        String natureza = novo.valor().signum() > 0 ? "RECEITA" : "DESPESA";
        if (categoria == null || categoria.arquivada() || !categoria.natureza().equals(natureza)) {
            throw new RegraDeNegocioException("Uma das categorias escolhidas foi arquivada ou excluída. Revise as linhas marcadas.",
                    "CATEGORIA_INVALIDA").com("linha", novo.linha()).com("data", novo.data().format(DIA_E_MES));
        }
    }
}
