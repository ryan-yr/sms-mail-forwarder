package com.local.smsmail;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Date;
import java.util.Properties;

public final class SmtpSender {
    public static void send(Config cfg,String recipient,String subject,String body,String id) throws MessagingException {
        cfg.validate();
        if(!Logic.email(recipient))throw new AddressException("Invalid destination");
        Properties p=new Properties();p.setProperty("mail.smtp.host",cfg.host);p.setProperty("mail.smtp.port",Integer.toString(cfg.port));
        p.setProperty("mail.smtp.auth","true");p.setProperty("mail.smtp.connectiontimeout","15000");p.setProperty("mail.smtp.timeout","20000");p.setProperty("mail.smtp.writetimeout","20000");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");p.setProperty("mail.smtp.ssl.protocols","TLSv1.2");
        p.setProperty("mail.smtp.ssl.enable",Boolean.toString(!cfg.startTls));
        p.setProperty("mail.smtp.starttls.enable",Boolean.toString(cfg.startTls));p.setProperty("mail.smtp.starttls.required",Boolean.toString(cfg.startTls));
        Session session=Session.getInstance(p);session.setDebug(false);
        MimeMessage message=new MimeMessage(session) {
            @Override protected void updateMessageID() throws MessagingException {setHeader("Message-ID","<"+id+"@smsmail.local>");}
        };
        message.setFrom(new InternetAddress(cfg.account));message.setRecipient(Message.RecipientType.TO,new InternetAddress(recipient));
        message.setSubject(subject,"UTF-8");message.setText(body,"UTF-8");message.setSentDate(new Date());message.saveChanges();
        Transport transport=session.getTransport("smtp");
        try {transport.connect(cfg.host,cfg.port,cfg.account,cfg.password);transport.sendMessage(message,message.getAllRecipients());}
        finally {try {transport.close();} catch(MessagingException ignored) { /* A QUIT failure must not undo an accepted DATA delivery. */ }}
    }
    private SmtpSender() {}
}
