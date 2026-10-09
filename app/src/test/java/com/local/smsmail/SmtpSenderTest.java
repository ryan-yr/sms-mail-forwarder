package com.local.smsmail;

import org.junit.*;
import static org.junit.Assert.*;
import javax.net.ssl.*;
import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Properties;
import java.util.concurrent.*;

/** Real local TLS SMTP exchange; no external mail or credentials are used. */
public final class SmtpSenderTest {
    private SSLContext original;
    private SSLContext tls;
    @Before public void setup() throws Exception {
        original=SSLContext.getDefault();KeyStore keys=KeyStore.getInstance("PKCS12");
        try(InputStream input=getClass().getResourceAsStream("/smtp-test.p12")){keys.load(input,"test-password".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"test-password".toCharArray());
        TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);SSLContext.setDefault(tls);
    }
    @After public void reset(){SSLContext.setDefault(original);}
    @Test public void encryptedSmtpPreservesUnicodeLongBodyAndDestination() throws Exception {
        try(SSLServerSocket server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0)) {
            server.setSoTimeout(6000);CompletableFuture<String> received=new CompletableFuture<>();
            Thread thread=new Thread(()->serve(server,received));thread.start();
            Config cfg=config(server.getLocalPort());
            String body="发送号码：1068\n短信正文：\n"+"你好，长短信。".repeat(1000)+"\n.line";
            SmtpSender.send(cfg,"recipient@example.org","中文测试",body,"stable-id");
            String raw=received.get(5,TimeUnit.SECONDS);
            MimeMessage mail=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            assertEquals("中文测试",mail.getSubject());assertEquals("recipient@example.org",mail.getAllRecipients()[0].toString());
            assertEquals("<stable-id@smsmail.local>",mail.getMessageID());
            assertEquals(body,((String)mail.getContent()).replace("\r\n","\n").stripTrailing());
            thread.join(3000);assertFalse(thread.isAlive());
        }
    }
    @Test public void certificateForDifferentHostIsRejected() throws Exception {
        try(SSLServerSocket server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0)) {
            server.setSoTimeout(6000);CompletableFuture<String> received=new CompletableFuture<>();Thread thread=new Thread(()->serve(server,received));thread.start();
            Config cfg=config(server.getLocalPort());cfg.host="127.0.0.1";
            try {SmtpSender.send(cfg,"recipient@example.org","test","body","id");fail("Hostname verification must reject the certificate");}
            catch(MessagingException expected){assertFalse(received.isDone() && !received.isCompletedExceptionally());}
            thread.join(3000);
        }
    }
    private Config config(int port){Config c=new Config();c.host="localhost";c.port=port;c.account="sender@example.org";c.password="fixture-password";c.recipient="recipient@example.org";return c;}
    private void serve(SSLServerSocket server,CompletableFuture<String> received){
        try(SSLSocket socket=(SSLSocket)server.accept()) {
            socket.setSoTimeout(6000);BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
            reply(out,"220 localhost fixture");String line;boolean authenticated=false;
            while((line=input.readLine())!=null){
                if(line.startsWith("EHLO"))reply(out,"250-localhost\r\n250-AUTH LOGIN PLAIN\r\n250 SIZE 1000000");
                else if(line.equals("AUTH LOGIN")){
                    reply(out,"334 VXNlcm5hbWU6");String user=new String(java.util.Base64.getDecoder().decode(input.readLine()),StandardCharsets.UTF_8);
                    reply(out,"334 UGFzc3dvcmQ6");String pass=new String(java.util.Base64.getDecoder().decode(input.readLine()),StandardCharsets.UTF_8);
                    authenticated=user.equals("sender@example.org") && pass.equals("fixture-password");reply(out,authenticated?"235 authenticated":"535 auth failed");
                } else if(line.startsWith("AUTH PLAIN")){
                    String encoded=line.length()>11?line.substring(11):null;if(encoded==null){reply(out,"334 ");encoded=input.readLine();}
                    String decoded=new String(java.util.Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);authenticated=decoded.endsWith("sender@example.org\0fixture-password");reply(out,authenticated?"235 authenticated":"535 auth failed");
                } else if(line.startsWith("MAIL FROM:")||line.startsWith("RCPT TO:"))reply(out,authenticated?"250 accepted":"530 auth required");
                else if(line.equals("DATA")){
                    reply(out,"354 send data");StringBuilder raw=new StringBuilder();
                    while((line=input.readLine())!=null && !line.equals(".")){if(line.startsWith(".."))line=line.substring(1);raw.append(line).append("\r\n");}
                    received.complete(raw.toString());reply(out,"250 delivered");
                } else if(line.equals("QUIT")){reply(out,"221 bye");break;}
                else reply(out,"250 ok");
            }
        } catch(Exception e){received.completeExceptionally(e);}
    }
    private void reply(BufferedWriter out,String text) throws IOException {out.write(text+"\r\n");out.flush();}
}
