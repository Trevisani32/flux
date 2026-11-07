package com.flux.compartilhado.email;

import java.io.UnsupportedEncodingException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EnvioDeEmailSmtp implements EnvioDeEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvioDeEmailSmtp.class);

    private final JavaMailSender mailSender;
    private final PropriedadesEmail propriedades;

    public EnvioDeEmailSmtp(JavaMailSender mailSender, PropriedadesEmail propriedades) {
        this.mailSender = mailSender;
        this.propriedades = propriedades;
    }

    @Override
    public void enviar(MensagemDeEmail mensagem) {
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(propriedades.remetente(), "Flux");
            helper.setTo(mensagem.para());
            helper.setSubject(mensagem.assunto());
            helper.setText(mensagem.texto(), mensagem.html());
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("Não foi possível montar o e-mail", ex);
        }
        mailSender.send(mime);
        LOGGER.info("E-mail enviado: \"{}\" para {}", mensagem.assunto(), MascaraDeEmail.mascarar(mensagem.para()));
    }
}
