package com.flux.contas;

import java.util.Optional;

/**
 * Uso de uma conta por outro módulo (lançamentos na Fatia 03b; cartões na 05a; depois recorrências...).
 * Cada módulo que referencia contas implementa. Conta com algum uso não pode ser excluída, só arquivada.
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

    /**
     * Fatia 05a: o papel da conta noutro módulo, para a frase "… porque é {papel}." (ex.: "a conta de
     * pagamento de 1 cartão"), ou vazio. Com uso e papel, a frase junta os dois: "… porque tem 34
     * lançamentos e é a conta de pagamento de 1 cartão."
     */
    default Optional<String> papel(Long usuarioId, Long contaId) {
        return Optional.empty();
    }
}
