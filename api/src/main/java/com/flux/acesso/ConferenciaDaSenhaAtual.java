package com.flux.acesso;

/**
 * Confere a senha atual do usuário, com o limite de 10 conferências em 15 minutos por usuário (Fatia 02), o mesmo de trocar
 * a senha, trocar o e-mail e excluir a conta. Conta a tentativa antes de olhar a senha, certa ou errada, para um 429 não
 * revelar se ela estava certa. Lança LimiteExcedidoException (429), RegraDeNegocioException "Senha atual incorreta." (422)
 * ou RecursoNaoEncontradoException (404).
 */
public interface ConferenciaDaSenhaAtual {

    void conferir(Long usuarioId, String senhaAtual);
}
