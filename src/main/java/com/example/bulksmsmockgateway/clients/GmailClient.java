package com.example.bulksmsmockgateway.clients;

import com.example.bulksmsmockgateway.config.SandboxSettings;
import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class GmailClient {
    private final JavaMailSender mail;
    private final SandboxSettings settings;

    public GmailClient(JavaMailSender mail, SandboxSettings settings, @Value("${spring.mail.password}") String password) {
        SandboxSettings.required(password, "GMAIL_APP_PASSWORD");
        this.mail = mail;
        this.settings = settings;
    }

    public void send(GatewayData.Message sms) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(settings.emailFrom());
        email.setTo(settings.emailTo());
        email.setSubject("BulkSMS sandbox message " + sms.id());
        email.setText("SMS from %s\nSMS to %s\nEncoding %s\n\n%s"
                .formatted(sms.origin(), sms.destination(), sms.encoding(), sms.body()));
        mail.send(email);
    }
}
