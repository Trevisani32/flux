package com.flux.importacao.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.importacao.interno.dto.AmostraResponse;
import com.flux.importacao.interno.leitura.ExtratoLido;
import com.flux.importacao.interno.leitura.FormatoCsv;
import com.flux.importacao.interno.leitura.LeitorCsv;
import com.flux.importacao.interno.leitura.TextoDoArquivo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O perfil de CSV de cada conta e a amostra do passo "Como ler este CSV" (decisões 6 e 12). */
@Service
public class PerfilCsvService {

    private static final int LINHAS_DA_AMOSTRA = 6;

    private final PerfilCsvRepository perfis;
    private final ContasConsulta contas;
    private final Clock clock;

    public PerfilCsvService(PerfilCsvRepository perfis, ContasConsulta contas, Clock clock) {
        this.perfis = perfis;
        this.contas = contas;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FormatoCsv buscar(Long usuarioId, Long contaId) {
        contaDoUsuario(usuarioId, contaId);
        return perfis.findByContaIdAndUsuarioId(contaId, usuarioId).map(PerfilCsv::formato)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esta conta ainda não tem perfil de CSV."));
    }

    /** Cria ou troca o perfil da conta ativa, dentro da trava de contas (a conta não some no meio). */
    @Transactional
    public FormatoCsv salvar(Long usuarioId, Long contaId, FormatoCsv formato) {
        exigirColunasDiferentes(formato);
        contas.travar(usuarioId);
        ContaResumo conta = contaDoUsuario(usuarioId, contaId);
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Reative a conta para importar nela.", "CONTA_ARQUIVADA");
        }
        PerfilCsv perfil = perfis.findByContaIdAndUsuarioId(contaId, usuarioId).orElseGet(() -> new PerfilCsv(usuarioId, contaId));
        perfil.alterar(formato, Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        return perfis.save(perfil).formato();
    }

    /**
     * As primeiras linhas do CSV e a primeira movimentação lida com o formato pedido (ou com o
     * sugerido: o do Nubank pelo cabeçalho, ou um palpite pelo separador mais frequente).
     */
    public AmostraResponse amostra(byte[] bytes, FormatoCsv pedido) {
        if (bytes.length == 0 || TextoDoArquivo.binario(bytes)) {
            throw new RegraDeNegocioException("Não reconhecemos este arquivo. Envie o extrato da conta em OFX ou CSV.",
                    "ARQUIVO_INVALIDO");
        }
        String texto = TextoDoArquivo.decodificar(bytes);
        if (TextoDoArquivo.ofx(texto)) {
            throw new RegraDeNegocioException("Este arquivo é um OFX: ele não precisa de perfil.", "ARQUIVO_OFX");
        }
        if (TextoDoArquivo.faturaDoNubank(texto)) {
            throw new RegraDeNegocioException("Este CSV parece ser a fatura do cartão Nubank. A importação de faturas chega junto "
                    + "com os cartões.", "EXTRATO_DE_CARTAO");
        }
        boolean nubank = TextoDoArquivo.cabecalhoDoNubank(texto);
        FormatoCsv formato = pedido != null ? pedido : nubank ? FormatoCsv.NUBANK_CONTA : sugerido(texto);
        List<List<String>> linhas = LeitorCsv.registros(texto, formato.separador().caractere()).stream()
                .limit(LINHAS_DA_AMOSTRA).toList();
        List<ExtratoLido.Linha> lidas = new LeitorCsv(formato).ler(texto).linhas();
        if (lidas.isEmpty()) {
            return new AmostraResponse(linhas, nubank ? "NUBANK_CONTA" : null, formato, null,
                    "O arquivo não tem nenhuma linha depois do cabeçalho.");
        }
        ExtratoLido.Linha primeira = lidas.getFirst();
        return primeira.legivel()
                ? new AmostraResponse(linhas, nubank ? "NUBANK_CONTA" : null, formato,
                        new AmostraResponse.PrimeiraLinha(primeira.data(), primeira.descricao(), primeira.valor()), null)
                : new AmostraResponse(linhas, nubank ? "NUBANK_CONTA" : null, formato, null,
                        "Linha " + primeira.numero() + ": " + primeira.erro() + ".");
    }

    private ContaResumo contaDoUsuario(Long usuarioId, Long contaId) {
        return contas.buscar(usuarioId, contaId).orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }

    /**
     * Data, descrição, valor, saídas e identificador em colunas diferentes. Entradas e saídas na
     * mesma coluna têm a mensagem do campo das saídas (mockup, "Campos do perfil").
     */
    private static void exigirColunasDiferentes(FormatoCsv formato) {
        if (formato.colunaSaidas() != null && formato.colunaSaidas().equals(formato.colunaValor())) {
            throw new CampoInvalidoException("colunaSaidas", "Use colunas diferentes para entradas e saídas.");
        }
        List<Integer> colunas = Stream.of(formato.colunaData(), formato.colunaDescricao(), formato.colunaValor(),
                formato.colunaSaidas(), formato.colunaIdentificador()).filter(Objects::nonNull).toList();
        Set<Integer> diferentes = new HashSet<>(colunas);
        if (diferentes.size() != colunas.size()) {
            throw new CampoInvalidoException("colunas", "Use uma coluna diferente para cada campo.");
        }
    }

    /** Palpite: o separador que mais aparece no cabeçalho; data na 1, descrição na 2, valor na 3. */
    private static FormatoCsv sugerido(String texto) {
        String primeira = texto.lines().filter(linha -> !linha.isBlank()).findFirst().orElse("");
        long pontoEVirgula = primeira.chars().filter(c -> c == ';').count();
        long tabs = primeira.chars().filter(c -> c == '\t').count();
        long virgulas = primeira.chars().filter(c -> c == ',').count();
        FormatoCsv.Separador separador = pontoEVirgula > virgulas && pontoEVirgula >= tabs ? FormatoCsv.Separador.PONTO_E_VIRGULA
                : tabs > virgulas ? FormatoCsv.Separador.TAB : FormatoCsv.Separador.VIRGULA;
        FormatoCsv.SeparadorDecimal decimal = separador == FormatoCsv.Separador.VIRGULA ? FormatoCsv.SeparadorDecimal.PONTO
                : FormatoCsv.SeparadorDecimal.VIRGULA;
        return new FormatoCsv(separador, 1, 1, FormatoCsv.FormatoData.DD_MM_AAAA, 2, 3, null, null, decimal);
    }
}
