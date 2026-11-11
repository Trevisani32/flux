package com.flux.importacao.interno;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    Optional<Importacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Histórico (decisão 33): a mais recente primeiro. */
    List<Importacao> findAllByUsuarioIdOrderByCriadaEmDescIdDesc(Long usuarioId);

    /** As origens das linhas já importadas de uma revisão, a mais recente primeiro. */
    List<Importacao> findAllByIdInAndUsuarioIdOrderByCriadaEmDescIdDesc(Collection<Long> ids, Long usuarioId);

    /** Aviso de arquivo repetido (decisão 25): o mesmo arquivo, em qualquer conta, não desfeito. */
    Optional<Importacao> findFirstByUsuarioIdAndHashArquivoAndDesfeitaEmIsNullOrderByCriadaEmDesc(Long usuarioId, String hashArquivo);

    /** Aviso de extrato de outra conta (decisão 11): o mesmo BANKID e ACCTID, não desfeito, o mais recente primeiro. */
    List<Importacao> findAllByUsuarioIdAndExtratoHashAndDesfeitaEmIsNullOrderByCriadaEmDescIdDesc(Long usuarioId, String extratoHash);
}
