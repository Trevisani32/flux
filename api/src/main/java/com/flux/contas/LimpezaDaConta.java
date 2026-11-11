package com.flux.contas;

/**
 * O que outro módulo apaga junto com a conta, sem que isso seja uso dela (Fatia 04a: os
 * lançamentos importados que o usuário excluiu, os ignorados). O {@code ContaService} chama
 * todas as implementações depois de conferir o uso ({@link UsoDaConta}) e antes de excluir,
 * na mesma transação e dentro da trava de contas. Os registros que não são uso nem prendem a
 * conta (importações e perfis de CSV) saem pela FK {@code ON DELETE CASCADE}.
 */
public interface LimpezaDaConta {

    /** Apaga o que este módulo guarda da conta e não deve impedir a exclusão. */
    void antesDeExcluir(Long usuarioId, Long contaId);
}
