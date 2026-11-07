package com.flux.acesso.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.flux.compartilhado.email.CorpoDeEmail;
import com.flux.compartilhado.email.MascaraDeEmail;
import com.flux.compartilhado.email.MensagemDeEmail;
import org.springframework.stereotype.Component;

/** Textos dos e-mails da conta (mockup da Fatia 02, quadro "E-mails"). */
@Component
public class ModelosDeEmail {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.of("pt", "BR"));
    private static final String COPIE = "Se o botão não funcionar, copie este endereço no navegador:";

    private final PropriedadesConta propriedades;
    private final Clock clock;

    public ModelosDeEmail(PropriedadesConta propriedades, Clock clock) {
        this.propriedades = propriedades;
        this.clock = clock;
    }

    public MensagemDeEmail confirmacaoDeCadastro(String para, String nome, String token) {
        String link = propriedades.urlBase() + "/confirmar-email#token=" + token;
        return CorpoDeEmail.comTitulo("Confirme seu e-mail")
                .paragrafo("Olá, " + primeiroNome(nome) + ". Falta só um passo para usar o Flux: confirme que este e-mail é seu.")
                .botao("Confirmar e-mail", link)
                .pequeno("O link vale por 24 horas e só pode ser usado uma vez. " + COPIE)
                .endereco(link)
                .rodape("Você recebeu este e-mail porque alguém criou uma conta no Flux com este endereço. "
                        + "Se não foi você, ignore: o cadastro é apagado em 7 dias.")
                .para(para, "Confirme seu e-mail no Flux");
    }

    public MensagemDeEmail redefinicaoDeSenha(String para, String nome, String token) {
        String link = propriedades.urlBase() + "/redefinir-senha#token=" + token;
        return CorpoDeEmail.comTitulo("Redefinir sua senha")
                .paragrafo("Olá, " + primeiroNome(nome) + ". Recebemos um pedido para criar uma senha nova para a sua conta.")
                .botao("Criar senha nova", link)
                .pequeno("O link vale por 30 minutos e só pode ser usado uma vez. Ao salvar a senha nova, "
                        + "todas as sessões abertas são encerradas. " + COPIE)
                .endereco(link)
                .rodape("Se não foi você, ignore este e-mail: a sua senha continua a mesma.")
                .para(para, "Redefinir sua senha do Flux");
    }

    public MensagemDeEmail confirmacaoDeNovoEmail(String para, String nome, String token) {
        String link = propriedades.urlBase() + "/confirmar-email#token=" + token;
        return CorpoDeEmail.comTitulo("Confirme seu novo e-mail")
                .paragrafo("Olá, " + primeiroNome(nome) + ". Recebemos um pedido para usar este endereço na sua conta do Flux.")
                .botao("Confirmar novo e-mail", link)
                .pequeno("O link vale por 24 horas e só pode ser usado uma vez. Até a confirmação, "
                        + "você continua entrando com o e-mail atual. " + COPIE)
                .endereco(link)
                .rodape("Se não foi você que pediu, ignore este e-mail: a conta continua com o e-mail atual.")
                .para(para, "Confirme seu novo e-mail no Flux");
    }

    public MensagemDeEmail tentativaDeCadastro(String para, String nome) {
        return CorpoDeEmail.comTitulo("Tentativa de cadastro com o seu e-mail")
                .paragrafo("Olá, " + primeiroNome(nome) + ". Alguém tentou criar uma conta no Flux com este e-mail. "
                        + "Se foi você, é só entrar ou redefinir a senha. Se não foi, ignore.")
                .botao("Entrar", propriedades.urlBase() + "/entrar")
                .link("Esqueci a senha", propriedades.urlBase() + "/esqueci-a-senha")
                .para(para, "Tentativa de cadastro com o seu e-mail");
    }

    public MensagemDeEmail avisoDeTrocaDeEmail(String para, String nome, String emailNovo) {
        return CorpoDeEmail.comTitulo("Pedido de troca do e-mail")
                .paragrafo("Olá, " + primeiroNome(nome) + ". Pediram para trocar o e-mail da sua conta para "
                        + MascaraDeEmail.mascarar(emailNovo) + ". A troca só vale depois de confirmada no novo endereço. "
                        + "Se não foi você, redefina a sua senha: isso cancela a troca.")
                .botao("Esqueci a senha", propriedades.urlBase() + "/esqueci-a-senha")
                .para(para, "Pedido de troca do e-mail da sua conta");
    }

    public MensagemDeEmail contaExcluida(String para, String nome, Instant excluidaEm) {
        CorpoDeEmail corpo = CorpoDeEmail.comTitulo("Sua conta foi excluída")
                .paragrafo("Olá, " + primeiroNome(nome) + ". A sua conta e todos os seus dados foram apagados em "
                        + DATA_HORA.format(excluidaEm.atZone(clock.getZone())) + ".");
        String contato = propriedades.controlador().email();
        if (!contato.isEmpty()) {
            corpo.paragrafo("Se não foi você, fale com " + contato + ".");
        }
        return corpo.para(para, "Sua conta do Flux foi excluída");
    }

    static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.strip();
        String primeiro = limpo.isEmpty() ? "" : limpo.split("\\s+")[0];
        // Só letras, apóstrofo e hífen: o e-mail vai para endereços que ninguém confirmou, e um
        // nome como "www.golpe.example" viraria link no cliente de e-mail.
        return primeiro.matches("[\\p{L}\\p{M}'’-]{1,40}") ? primeiro : "Usuário";
    }
}
