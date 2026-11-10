package com.flux.contas;

import java.util.List;
import java.util.Optional;

/**
 * Leitura de contas para outros módulos (lançamentos, desde a Fatia 03b). Conta de outro
 * usuário é tratada como inexistente (ADR 0007).
 */
public interface ContasConsulta {

    /**
     * Trava as alterações de contas do usuário até o fim da transação: é a mesma trava que a
     * exclusão de conta e a edição da data do saldo inicial pegam (ADR 0012). Quem grava um
     * uso de conta (um lançamento, por exemplo) chama isto antes de ler a conta. Exige uma
     * transação aberta.
     */
    void travar(Long usuarioId);

    /** A conta do usuário, ativa ou arquivada; vazio se não existe ou é de outro usuário. */
    Optional<ContaResumo> buscar(Long usuarioId, Long contaId);

    /** Todas as contas do usuário, ativas e arquivadas, sem ordem definida. */
    List<ContaResumo> listar(Long usuarioId);
}
