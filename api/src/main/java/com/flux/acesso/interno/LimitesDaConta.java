package com.flux.acesso.interno;

import java.time.Duration;

import com.flux.compartilhado.limite.LimiteDeTentativas;
import com.flux.compartilhado.limite.LimiteExcedidoException;
import org.springframework.stereotype.Component;

/**
 * Limites aprovados para a Fatia 02. Um pedido barrado (429) não conta: se o limite do
 * e-mail estoura, a tentativa já contada no IP é devolvida.
 */
@Component
public class LimitesDaConta {

    private static final Duration QUINZE_MINUTOS = Duration.ofMinutes(15);
    private static final Duration UMA_HORA = Duration.ofHours(1);

    private final LimiteDeTentativas limite;
    private final boolean porIp;

    public LimitesDaConta(LimiteDeTentativas limite, PropriedadesConta propriedades) {
        this.limite = limite;
        this.porIp = propriedades.limitePorIp();
    }

    /** Login: 50 por IP e 10 por e-mail em 15 min. Quem chama devolve se a senha conferir. */
    public void entrar(String ip, String email) {
        consumirIpEEmail("entrar", ip, 50, email, 10, QUINZE_MINUTOS);
    }

    public void devolverEntrar(String ip, String email) {
        if (porIp) {
            limite.devolver("entrar:ip:" + ip);
        }
        limite.devolver("entrar:email:" + email);
    }

    /** Criar conta: 10 por IP e 3 por e-mail por hora. */
    public void cadastrar(String ip, String email) {
        consumirIpEEmail("cadastro", ip, 10, email, 3, UMA_HORA);
    }

    /** Reenviar confirmação: 10 por IP e 3 por e-mail por hora. */
    public void reenviarConfirmacao(String ip, String email) {
        consumirIpEEmail("reenvio", ip, 10, email, 3, UMA_HORA);
    }

    /** Esqueci a senha: 10 por IP e 3 por e-mail por hora. */
    public void esqueciASenha(String ip, String email) {
        consumirIpEEmail("esqueci", ip, 10, email, 3, UMA_HORA);
    }

    /** Redefinir a senha: 10 por IP em 15 min (o pedido traz o token, não o e-mail). */
    public void redefinirSenha(String ip) {
        if (porIp) {
            limite.consumir("redefinicao:ip:" + ip, 10, QUINZE_MINUTOS);
        }
    }

    /** Conferir a senha atual (trocar senha, trocar e-mail, excluir conta): 10 por usuário em 15 min. */
    public void conferirSenhaAtual(Long usuarioId) {
        limite.consumir("senha-atual:usuario:" + usuarioId, 10, QUINZE_MINUTOS);
    }

    /**
     * Pedir a troca de e-mail: o limite da senha atual e, como o pedido manda e-mail, 3 por
     * usuário e 3 por endereço novo por hora. Um pedido barrado não conta em nenhum dos três.
     * Quem chama devolve os dois da troca se o pedido falhar depois (senha errada, e-mail igual):
     * só conta a troca que manda e-mail.
     */
    public void pedirTrocaDeEmail(Long usuarioId, String emailNovo) {
        String senhaAtual = "senha-atual:usuario:" + usuarioId;
        String porUsuario = "troca:usuario:" + usuarioId;
        limite.consumir(senhaAtual, 10, QUINZE_MINUTOS);
        try {
            limite.consumir(porUsuario, 3, UMA_HORA);
        } catch (LimiteExcedidoException ex) {
            limite.devolver(senhaAtual);
            throw ex;
        }
        try {
            limite.consumir("troca:email:" + emailNovo, 3, UMA_HORA);
        } catch (LimiteExcedidoException ex) {
            limite.devolver(porUsuario);
            limite.devolver(senhaAtual);
            throw ex;
        }
    }

    public void devolverTrocaDeEmail(Long usuarioId, String emailNovo) {
        limite.devolver("troca:usuario:" + usuarioId);
        limite.devolver("troca:email:" + emailNovo);
    }

    private void consumirIpEEmail(String acao, String ip, int maximoPorIp, String email, int maximoPorEmail,
                                  Duration janela) {
        if (porIp) {
            limite.consumir(acao + ":ip:" + ip, maximoPorIp, janela);
        }
        try {
            limite.consumir(acao + ":email:" + email, maximoPorEmail, janela);
        } catch (LimiteExcedidoException ex) {
            if (porIp) {
                limite.devolver(acao + ":ip:" + ip);
            }
            throw ex;
        }
    }
}
