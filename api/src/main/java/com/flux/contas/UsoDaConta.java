package com.flux.contas;

import java.util.Optional;

/**
 * Uso de uma conta por outro módulo (lançamentos na Fatia 03b; depois cartões,
 * recorrências...). Cada módulo que referencia contas implementa. Conta com algum uso
 * não pode ser excluída, só arquivada.
 *
 * <p>Sem corrida entre conferir e excluir: o {@code ContaService} confere o uso e exclui
 * dentro da trava do usuário, {@code pg_advisory_xact_lock(hashtextextended('contas:' ||
 * usuarioId, 0))}. Quem implementa grava um uso novo (um lançamento na conta, por exemplo)
 * pegando antes essa mesma trava, na mesma transação. A FK do uso para {@code contas} é
 * {@code ON DELETE RESTRICT}, nunca {@code CASCADE}: um uso gravado fora da trava faz a
 * exclusão falhar, sem apagar nada.
 */
public interface UsoDaConta {

    /**
     * Descrição curta do uso, para entrar na frase "Esta conta não pode ser excluída
     * porque tem {descrição}." (ex.: "128 lançamentos"), ou vazio se a conta não é usada.
     */
    Optional<String> uso(Long usuarioId, Long contaId);
}
