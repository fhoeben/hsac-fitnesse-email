package nl.hsac.fitnesse.fixture.slim;

import nl.hsac.fitnesse.fixture.util.ThrowingFunction;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Fixture to check for mails received in imap mailbox.
 */
public class EmailFixture extends SlimFixture {
    protected static final DateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected static final FlagTerm NON_DELETED_TERM = new FlagTerm(new Flags(Flags.Flag.DELETED), false);
    private String folder = "inbox";
    private Store store;
    private Message lastMessage;

    private Date receivedAfter = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24);
    private String expectedTo;
    private String expectedSubject;

    /**
     * Creates new fixture instance with "imaps" protocol.
     */
    public EmailFixture() {
        this("imaps");
    }

    /**
     * Creates new.
     * @param protocol protocol to use.
     */
    public EmailFixture(String protocol) {
        this(getStore(protocol));
    }

    /**
     * Creates new.
     * @param store store to use.
     */
    public EmailFixture(Store store) {
        this.store = store;
    }

    /**
     * | connect to host | <i>host</i> | with user | <i>username</i> | and password | <i>password</i> |
     */
    public void connectToHostWithUserAndPassword(String host, String username, String password) {
        try {
            store.connect(host, username, password);
        } catch (MessagingException e) {
            throw new StopTestException("Cannot connect to mailserver", e);
        }
    }

    public String sentDate() {
        return applyToLastMessage(m -> DATE_TIME_FORMATTER.format(m.getSentDate()));
    }

    public String sender() {
        return applyToLastMessage(m -> String.valueOf(m.getFrom()[0]));
    }

    public String subject() {
        return applyToLastMessage(m -> m.getSubject());
    }

    public String receivedDate() {
        return applyToLastMessage(m -> DATE_TIME_FORMATTER.format(m.getReceivedDate()));
    }

    public String toRecipient() {
        return applyToLastMessage(m -> String.valueOf(m.getRecipients(Message.RecipientType.TO)[0]));
    }

    public String ccRecipient() {
        return applyToLastMessage(m -> {
            Address[] ccRecipients = m.getRecipients(Message.RecipientType.CC);
            if (ccRecipients.length == 0) {
                throw new SlimFixtureException(false, "Message has no CC recipients");
            }
            return String.valueOf(ccRecipients[0]);
        });
    }

    public String body() {
        String result = bodyText();
        return getEnvironment().getHtml(result);
    }

    public String bodyText() {
        return applyToLastMessage(this::getBody);
    }

    public boolean retrieveLastMessage() {
        lastMessage = null;
        SearchTerm searchTerm = getSearchTerm();
        try {
            Message[] messages = openFolder().search(searchTerm);
            boolean result = messages.length > 0;
            if (result) {
                lastMessage = messages[messages.length - 1];
            }
            return result;
        } catch (MessagingException e) {
            throw new SlimFixtureException("Unable to retrieve messages", e);
        }
    }

    public boolean retrieveMessagesUntilMatchFound() {
        return repeatUntil(new FunctionalCompletion(this::retrieveLastMessage));
    }

    protected SearchTerm getSearchTerm() {
        SearchTerm term = NON_DELETED_TERM;
        if (receivedAfter != null) {
            term = new AndTerm(term, new ReceivedDateTerm(ComparisonTerm.GT, receivedAfter));
        }
        if (expectedSubject != null) {
            term = new AndTerm(term, new SubjectTerm(expectedSubject));
        }
        if (expectedTo != null) {
            term = new AndTerm(term, new RecipientStringTerm(Message.RecipientType.TO, expectedTo));
        }
        return term;
    }

    protected <T> T applyToLastMessage(ThrowingFunction<Message, T, Exception> function) {
        T result = null;
        if (lastMessage != null) {
            result = function.applyWrapped(lastMessage, SlimFixtureException::new);
        }
        return result;
    }

    protected Message getLastMessage() {
        return lastMessage;
    }

    protected static Store getStore(String protocol) {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);
        try {
            return session.getStore(protocol);
        } catch (NoSuchProviderException e) {
            throw new StopTestException("Unsupported mail protocol: " + protocol, e);
        }
    }

    protected Folder openFolder() {
        try {
            Folder inbox = store.getFolder(folder);
            inbox.open(Folder.READ_ONLY);
            return inbox;
        } catch (MessagingException e) {
            throw new StopTestException("Unable to open folder: " + folder);
        }
    }

    protected String getBody(Message msg) {
        try {
            String message = "";
            if (msg != null) {
                Object msgContent = msg.getContent();
                if (msgContent instanceof MimeMultipart) {
                    Multipart multipart = (Multipart) msgContent;
                    message = IOUtils.toString(multipart.getBodyPart(0).getInputStream());
                } else {
                    message = msgContent.toString();
                }
            }
            return message;
        } catch (IOException ex) {
            throw new RuntimeException("Unable to get body of message", ex);
        } catch (MessagingException ex) {
            throw new RuntimeException("Unable to get body of message", ex);
        }
    }

    public Date getReceivedAfter() {
        return receivedAfter;
    }

    public void setReceivedAfter(Date receivedAfter) {
        this.receivedAfter = receivedAfter;
    }

    public void onlyMessagesReceivedAfter(String date) {
        try {
            Date rDate = StringUtils.isEmpty(date) ? null : DATE_TIME_FORMATTER.parse(date);
            setReceivedAfter(rDate);
        } catch (ParseException e) {
            throw new SlimFixtureException(false, "Unable to parse date: " + date, e);
        }
    }

    public String getExpectedTo() {
        return expectedTo;
    }

    public void onlyMessagesSentTo(String expectedTo) {
        this.expectedTo = cleanupValue(expectedTo);
    }

    public String getExpectedSubject() {
        return expectedSubject;
    }

    public void onlyMessagesWithSubject(String expectedSubject) {
        this.expectedSubject = expectedSubject;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }

    protected Store getStore() {
        return store;
    }
}
