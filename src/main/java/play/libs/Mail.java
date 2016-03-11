package play.libs;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;

import play.Logger;
import play.exceptions.MailException;

/**
 * Mail utils.
 * TODO implement
 */
public class Mail {

    public static Session session;
    public static boolean asynchronousSend = true;


    /**
     * Send an email
     */
    public static Future<Boolean> send(Email email) {
        try {
            email = buildMessage(email);

            /*
            if (Play.configuration.getProperty("mail.smtp", "").equals("mock") && Play.mode == Play.Mode.DEV) {
                Mock.send(email);
                return new Future<Boolean>() {

                    @Override
                    public boolean cancel(final boolean mayInterruptIfRunning) {
                        return false;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        return true;
                    }

                    @Override
                    public Boolean get() throws InterruptedException, ExecutionException {
                        return true;
                    }

                    @Override
                    public Boolean get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                        return true;
                    }
                };
            }
            */

            email.setMailSession(getSession());
            return sendMessage(email);
        } catch (final EmailException ex) {
            throw new MailException("Cannot send email", ex);
        }
    }

    /**
     *
     */
    public static Email buildMessage(final Email email) throws EmailException {

        final String from = findSmtpFrom();
        if (email.getFromAddress() == null && !StringUtils.isEmpty(from)) {
            email.setFrom(from);
        } else if (email.getFromAddress() == null) {
            throw new MailException("Please define a 'from' email address", new NullPointerException());
        }
        if ((email.getToAddresses() == null || email.getToAddresses().size() == 0) &&
            (email.getCcAddresses() == null || email.getCcAddresses().size() == 0)  &&
            (email.getBccAddresses() == null || email.getBccAddresses().size() == 0))
        {
            throw new MailException("Please define a recipient email address", new NullPointerException());
        }
        if (email.getSubject() == null) {
            throw new MailException("Please define a subject", new NullPointerException());
        }
        if (email.getReplyToAddresses() == null || email.getReplyToAddresses().size() == 0) {
            email.addReplyTo(email.getFromAddress().getAddress());
        }

        return email;
    }

    private static String findSmtpFrom() {
        // Play.configuration.getProperty("mail.smtp.from")
        return null;
    }

    public static Session getSession() {
        if (session == null) {
            final Properties props = new Properties();
            // Put a bogus value even if we are on dev mode, otherwise JavaMail will complain
            props.put("mail.smtp.host", findSmtpHost());

            String channelEncryption;
            if (isSmtps()) {
                // Backward compatibility before stable5
                channelEncryption = "starttls";
            } else {
                channelEncryption = findSmtpChannel();
            }

            if (channelEncryption.equals("clear")) {
                props.put("mail.smtp.port", "25");
            } else if (channelEncryption.equals("ssl")) {
                // port 465 + setup yes ssl socket factory (won't verify that the server certificate is signed with a root ca.)
                props.put("mail.smtp.port", "465");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "play.utils.YesSSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
            } else if (channelEncryption.equals("starttls")) {
                // port 25 + enable starttls + ssl socket factory
                props.put("mail.smtp.port", "25");
                props.put("mail.smtp.starttls.enable", "true");
                // can't install our socket factory. will work only with server that has a signed certificate
                // story to be continued in javamail 1.4.2 : https://glassfish.dev.java.net/issues/show_bug.cgi?id=5189
            }

            if (hasSmtpLocalhost()) {
                props.put("mail.smtp.localhost", findSmtpLocalhost());            //override defaults
            }
            if (hasSocketFactoryClass()) {
                props.put("mail.smtp.socketFactory.class", findSocketFactoryClass());
            }
            if (hasSmtpPort()) {
                props.put("mail.smtp.port", findSmtpPort());
            }
            final String user = findSmtpUser();
            final String password = findSmtpPassword();
            final String authenticator = findAuthenticator();
            session = null;

            if (authenticator != null) {
                props.put("mail.smtp.auth", "true");
                try {
                    session = null;//Session.getInstance(props, (Authenticator) Play.classloader.loadClass(authenticator).newInstance());
                } catch (final Exception e) {
                    Logger.error(e, "Cannot instanciate custom SMTP authenticator (%s)", authenticator);
                }
            }

            if (session == null) {
                if (user != null && password != null) {
                    props.put("mail.smtp.auth", "true");
                    session = Session.getInstance(props, new SMTPAuthenticator(user, password));
                } else {
                    props.remove("mail.smtp.auth");
                    session = Session.getInstance(props);
                }
            }

            if (Boolean.parseBoolean(debugStr())) {
                session.setDebug(true);
            }
        }
        return session;
    }


    private static String findSmtpChannel() {
        // Play.configuration.getProperty("mail.smtp.channel", "clear")
        return null;
    }

    private static String findSmtpPassword() {
        // Play.configuration.getProperty("mail.smtp.pass")
        return null;
    }

    private static String findSmtpPort() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    private static String findSocketFactoryClass() {
        // TODO 自動生成されたメソッド・スタブ
        return null;
    }

    private static String findSmtpUser() {
        // Play.configuration.getProperty("mail.smtp.user")
        return null;
    }

    private static boolean hasSmtpPort() {
        // Play.configuration.containsKey("mail.smtp.port")
        return false;
    }

    private static boolean hasSocketFactoryClass() {
        // Play.configuration.containsKey("mail.smtp.socketFactory.class")
        return false;
    }

    private static boolean hasSmtpLocalhost() {
        // TODO 自動生成されたメソッド・スタブ
        return false;
    }

    private static String findSmtpLocalhost() {
        // Play.configuration.containsKey("mail.smtp.localhost")
        return null;
    }

    private static String findAuthenticator() {
        // Play.configuration.getProperty("mail.smtp.authenticator");
        return null;
    }

    private static Object findSmtpHost() {
        // Play.configuration.getProperty("mail.smtp.host", "localhost")
        return null;
    }

    private static String debugStr() {
        // Play.configuration.getProperty("mail.debug", "false");
        return null;
    }

    private static boolean isSmtps() {
        return true;
        //Play.configuration.containsKey("mail.smtp.protocol") && Play.configuration.getProperty("mail.smtp.protocol", "smtp").equals("smtps");
    }

    /**
     * Send a JavaMail message
     *
     * @param msg An Email message
     */
    public static Future<Boolean> sendMessage(final Email msg) {
        if (asynchronousSend) {
            return executor.submit(new Callable<Boolean>() {

                @Override
                public Boolean call() {
                    try {
                        msg.setSentDate(new Date());
                        msg.send();
                        return true;
                    } catch (final Throwable e) {
                        final MailException me = new MailException("Error while sending email", e);
                        Logger.error(me, "The email has not been sent");
                        return false;
                    }
                }
            });
        } else {
            final StringBuffer result = new StringBuffer();
            try {
                msg.setSentDate(new Date());
                msg.send();
            } catch (final Throwable e) {
                final MailException me = new MailException("Error while sending email", e);
                Logger.error(me, "The email has not been sent");
                result.append("oops");
            }
            return new Future<Boolean>() {

                @Override
                public boolean cancel(final boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public Boolean get() throws InterruptedException, ExecutionException {
                    return result.length() == 0;
                }

                @Override
                public Boolean get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    return result.length() == 0;
                }
            };
        }
    }

    static ExecutorService executor = Executors.newCachedThreadPool();

    public static class SMTPAuthenticator extends Authenticator {

        private final String user;
        private final String password;

        public SMTPAuthenticator(final String user, final String password) {
            this.user = user;
            this.password = password;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password);
        }
    }

    public static class Mock {

        static Map<String, String> emails = new HashMap<String, String>();


        public static String getContent(final Part message) throws MessagingException,
                IOException {

            if (message.getContent() instanceof String) {
                return message.getContentType() + ": " + message.getContent() + " \n\t";
            } else if (message.getContent() != null && message.getContent() instanceof Multipart) {
                final Multipart part = (Multipart) message.getContent();
                String text = "";
                for (int i = 0; i < part.getCount(); i++) {
                    final BodyPart bodyPart = part.getBodyPart(i);
                    if (!Message.ATTACHMENT.equals(bodyPart.getDisposition())) {
                        text += getContent(bodyPart);
                    } else {
                        text += "attachment: \n" +
                       "\t\t name: " + (StringUtils.isEmpty(bodyPart.getFileName()) ? "none" : bodyPart.getFileName()) + "\n" +
                       "\t\t disposition: " + bodyPart.getDisposition() + "\n" +
                       "\t\t description: " +  (StringUtils.isEmpty(bodyPart.getDescription()) ? "none" : bodyPart.getDescription())  + "\n\t";
                    }
                }
                return text;
            }
            if (message.getContent() != null && message.getContent() instanceof Part) {
                if (!Message.ATTACHMENT.equals(message.getDisposition())) {
                    return getContent((Part) message.getContent());
                } else {
                    return "attachment: \n" +
                           "\t\t name: " + (StringUtils.isEmpty(message.getFileName()) ? "none" : message.getFileName()) + "\n" +
                           "\t\t disposition: " + message.getDisposition() + "\n" +
                           "\t\t description: " + (StringUtils.isEmpty(message.getDescription()) ? "none" : message.getDescription()) + "\n\t";
                }
            }

            return "";
        }


        static void send(final Email email) {

            try {
                final StringBuffer content = new StringBuffer();
                final Properties props = new Properties();
                props.put("mail.smtp.host", "myfakesmtpserver.com");

                final Session session = Session.getInstance(props);
                email.setMailSession(session);

                email.buildMimeMessage();

                final MimeMessage msg = email.getMimeMessage();
                msg.saveChanges();

                final String body = getContent(msg);

                content.append("From Mock Mailer\n\tNew email received by");


                content.append("\n\tFrom: " + email.getFromAddress().getAddress());
                content.append("\n\tReplyTo: " + email.getReplyToAddresses().get(0).getAddress());
                content.append("\n\tTo: ");
                for (final Object add : email.getToAddresses()) {
                    content.append(add.toString() + ", ");
                }
                // remove the last ,
                content.delete(content.length() - 2, content.length());
                if (email.getCcAddresses() != null && !email.getCcAddresses().isEmpty()) {
                    content.append("\n\tCc: ");
                    for (final Object add : email.getCcAddresses()) {
                        content.append(add.toString() + ", ");
                    }
                    // remove the last ,
                    content.delete(content.length() - 2, content.length());
                }
                 if (email.getBccAddresses() != null && !email.getBccAddresses().isEmpty()) {
                    content.append("\n\tBcc: ");
                    for (final Object add : email.getBccAddresses()) {
                        content.append(add.toString() + ", ");
                    }
                    // remove the last ,
                    content.delete(content.length() - 2, content.length());
                }
                content.append("\n\tSubject: " + email.getSubject());
                content.append("\n\t" + body);

                content.append("\n");
                Logger.info(content.toString());

                for (final Object add : email.getToAddresses()) {
                    content.append(", " + add.toString());
                    emails.put(((InternetAddress) add).getAddress(), content.toString());
                }

            } catch (final Exception e) {
                Logger.error(e, "error sending mock email");
            }

        }

        public static String getLastMessageReceivedBy(final String email) {
            return emails.get(email);
        }

        public static void reset(){
        	emails.clear();
        }
    }
}

