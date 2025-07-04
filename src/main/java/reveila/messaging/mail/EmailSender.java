package reveila.messaging.mail;

import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;


public class EmailSender {
    public static void main(String[] args) throws Exception {
        // SMTP server information
        String host = "smtp.example.com";
		String port = "";
        String username = "your_username";
        String password = "your_password";
        
        // Set properties
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        
		// Create message
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("from@example.com"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("to@example.com"));
        message.setSubject("Test Email");
        message.setText("Hello, this is a test email!");

        // Send message
        Transport.send(message);

        System.out.println("Email sent successfully.");
    }
}