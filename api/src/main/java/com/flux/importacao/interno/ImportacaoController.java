package com.flux.importacao.interno;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.importacao.interno.dto.AmostraResponse;
import com.flux.importacao.interno.dto.ConfirmarRequest;
import com.flux.importacao.interno.dto.ImportacaoResponse;
import com.flux.importacao.interno.dto.PreviaResponse;
import com.flux.importacao.interno.dto.ResultadoResponse;
import com.flux.importacao.interno.leitura.FormatoCsv;
import com.flux.lancamentos.LancamentosImportados;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importação de extratos (Fatia 04a). Os endpoints de arquivo são multipart, com o arquivo na
 * parte "arquivo" (até 5 MB: decisão 35); o resto é JSON.
 */
@RestController
@RequestMapping("/api/importacoes")
public class ImportacaoController {

    private final UsuarioAtual usuarioAtual;
    private final ImportacaoService importacaoService;
    private final PerfilCsvService perfilCsvService;

    public ImportacaoController(UsuarioAtual usuarioAtual, ImportacaoService importacaoService, PerfilCsvService perfilCsvService) {
        this.usuarioAtual = usuarioAtual;
        this.importacaoService = importacaoService;
        this.perfilCsvService = perfilCsvService;
    }

    /** A revisão: lê e classifica, sem gravar nada. Partes "arquivo" e "contaId". */
    @PostMapping("/previa")
    public PreviaResponse previa(@RequestPart MultipartFile arquivo, @RequestParam Long contaId) {
        return importacaoService.previa(usuarioAtual.id(), contaId, arquivo);
    }

    /** A confirmação: o mesmo arquivo e a parte JSON "pedido". */
    @PostMapping
    public ResponseEntity<ResultadoResponse> confirmar(@RequestPart MultipartFile arquivo,
                                                       @Valid @RequestPart ConfirmarRequest pedido) {
        ResultadoResponse resultado = importacaoService.confirmar(usuarioAtual.id(), arquivo, pedido);
        return ResponseEntity.created(URI.create("/api/importacoes")).body(resultado);
    }

    @GetMapping
    public List<ImportacaoResponse> listar() {
        return importacaoService.listar(usuarioAtual.id());
    }

    @PostMapping("/{id}/desfazer")
    public ImportacaoResponse desfazer(@PathVariable Long id) {
        return importacaoService.desfazer(usuarioAtual.id(), id);
    }

    @GetMapping("/{id}/excluidos")
    public List<LancamentosImportados.Excluido> excluidos(@PathVariable Long id) {
        return importacaoService.excluidos(usuarioAtual.id(), id);
    }

    /** As primeiras linhas do CSV; a parte JSON "formato" é opcional (sem ela, o sugerido). */
    @PostMapping("/amostra")
    public AmostraResponse amostra(@RequestPart MultipartFile arquivo,
                                   @Valid @RequestPart(required = false) FormatoCsv formato) {
        try {
            return perfilCsvService.amostra(arquivo.getBytes(), formato);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @GetMapping("/perfis/{contaId}")
    public FormatoCsv perfil(@PathVariable Long contaId) {
        return perfilCsvService.buscar(usuarioAtual.id(), contaId);
    }

    @PutMapping("/perfis/{contaId}")
    public FormatoCsv salvarPerfil(@PathVariable Long contaId, @Valid @RequestBody FormatoCsv formato) {
        return perfilCsvService.salvar(usuarioAtual.id(), contaId, formato);
    }
}
