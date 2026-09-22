package net.pieroxy.mom.utils;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import org.apache.commons.mail.util.MimeMessageParser;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MailTools {
    /**
     * Serializes the raw message (headers + body), never marking it \Seen as a side effect —
     * used by DKIM/DMARC verification, which needs the message exactly as received to
     * recompute a signature/body hash.
     * <p>
     * For a real IMAP message, reading the content triggers a FETCH BODY[] on the javax.mail
     * side; the session property "mail.imap.peek" does NOT apply to this path (verified
     * empirically: it only covers automatic prefetch when opening the folder, not an ad-hoc
     * content read like {@code writeTo()}) — {@link IMAPMessage#setPeek} set on THIS specific
     * message, right before reading, is the only mechanism that works. Without it, any message
     * inspected (whether matched by a rule or not) would end up silently marked as read.
     */
    public static byte[] readRawMessageWithoutMarkingSeen(Message message) throws MessagingException, IOException {
        if (message instanceof IMAPMessage) {
            ((IMAPMessage) message).setPeek(true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toByteArray();
    }

    public static String getFrom(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Address a : from) {
            if (sb.length()>1) sb.append(" ");
            sb.append(getNiceMailAddress(a));
        }
        return sb.toString();
    }

    /** Description of the From address for logs: never throws, returns a fallback text instead. */
    public static String describeFromSafely(Message message) {
        try {
            String from = getFrom(message);
            return from.isEmpty() ? "(unknown)" : from;
        } catch (MessagingException e) {
            return "?";
        }
    }

    public static String getNiceMailAddress(Address address) throws MessagingException {
        if (address instanceof InternetAddress) {
            InternetAddress ia = (InternetAddress) address;
            return ia.getPersonal() + " <" + ia.getAddress() + ">";
        } else {
            return address.toString();
        }
    }

    public static String getMailAddress(Address address) throws MessagingException {
        if (address instanceof InternetAddress) {
            InternetAddress ia = (InternetAddress) address;
            return  ia.getAddress();
        } else {
            return address.toString();
        }
    }

    public static Long getNextUid(Message m) throws MessagingException {
        Folder f = m.getFolder();
        if (f instanceof IMAPFolder) {
            return ((IMAPFolder) f).getUIDNext();
        }
        return null;
    }

    public static String getPlainContent(MimeMessage message) throws Exception {
        return new MimeMessageParser(message).parse().getPlainContent();
    }

    public static CharSequence getPlainContent(Object content) throws Exception {
        if (content instanceof String) return (String)content;
        if (content instanceof MimeMessage) return getPlainContent((MimeMessage) content);
        if (content instanceof MimeMultipart) return getTextFromMimeMultipart((MimeMultipart) content);
        return String.valueOf(content);
    }

    private static String getTextFromMimeMultipart(
            MimeMultipart mimeMultipart)  throws MessagingException, IOException {
        String result = "";
        for (int i = 0; i < mimeMultipart.getCount(); i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                return result + "\n" + bodyPart.getContent(); // without return, same text appears twice in my tests
            }
            result += parseBodyPart(bodyPart);
        }
        return result;
    }

    private static String parseBodyPart(BodyPart bodyPart) throws MessagingException, IOException {
        if (bodyPart.isMimeType("text/html")) {
            return "\n" + bodyPart.getContent().toString();
        }
        if (bodyPart.getContent() instanceof MimeMultipart){
            return getTextFromMimeMultipart((MimeMultipart)bodyPart.getContent());
        }

        return "";
    }
}
