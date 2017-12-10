package nl.hsac.fitnesse.fixture.slim;

import nl.hsac.fitnesse.fixture.slim.exceptions.CouldNotFindMessageException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.SearchTerm;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static java.lang.String.format;

/**
 * Fixture to check for mails received in imap mailbox.
 */
public class EmailFixture extends SlimFixture {
    private static Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private Date sentAfter = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24);
    private String folder = "inbox";
    private Store store;

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
        try {
            store = getStore(protocol);
        } catch (NoSuchProviderException e) {
            throw new StopTestException("Unsupported mail protocol: " + protocol, e);
        }
    }

    /**
     * | set imap mail provider with host | <i>host</i> | user | <i>username</i> | password | <i>password</i> |
     */
    public void setImapMailProviderWithHostPortUserPassword(String host, String username, String password) {
        try {
            store.connect(host, username, password);
        } catch (MessagingException e) {
            throw new StopTestException("Cannot connect to mailserver", e);
        }
    }

    protected Store getStore(String protocol) throws NoSuchProviderException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props);
        return session.getStore(protocol);
    }

    /**
     * | show | mail received by | <i>receiver</i> | with subject | <i>subject</i> |
     */
    public String mailReceivedByWithSubject(String receiver, String subject) {
        String recv = cleanupValue(receiver);
        return getMostRecentMessageBody(new SearchParameters(subject, recv, sentAfter));
    }

    private String getMostRecentMessageBody(SearchParameters params) {
        try {
            Folder folder = openFolder();
            Message msg = getMostRecentMessageMatching(folder, params);
            return getBody(msg);
        } catch (SlimFixtureException e) {
            throw e;
        } catch (Exception ex) {
            throw new SlimFixtureException(false, "No message found with search params: " + params, ex);
        }
    }

    private Folder openFolder() throws MessagingException {
        Folder inbox = store.getFolder(folder);
        inbox.open(Folder.READ_ONLY);
        return inbox;
    }

    private Message getMostRecentMessageMatching(Folder inbox, SearchParameters params) {
        List<Message> mails = getMessagesUntilMatchesFound(inbox, params);
        Collections.reverse(mails);
        return getFirstMessageSentAfter(mails, sentAfter);
    }

    private Message getFirstMessageSentAfter(List<Message> mails, Date minDate) {
        try {
            for (Message mail : mails) {
                if (mail.getSentDate().after(minDate)) {
                    return mail;
                }
            }
        } catch (MessagingException ex) {
            throw new RuntimeException("Exception looking for mail sent after: " + minDate, ex);
        }
        throw new SlimFixtureException(false, "No mail found sent after: " + minDate);
    }

    private List<Message> getMessagesUntilMatchesFound(Folder inbox, SearchParameters params) {
        List<Message> mails = new ArrayList<>();
        if (!repeatUntilNot(new FunctionalCompletion(
                mails::isEmpty,
                () -> mails.addAll(getMessagesMatching(inbox, params))))) {
            throw new CouldNotFindMessageException(inbox, params);
        }
        return mails;
    }

    private List<Message> getMessagesMatching(Folder inbox, SearchParameters params) {
        try {
            SearchTerm searchCondition = params.getSearchTerm();
            return Arrays.asList(inbox.search(searchCondition, inbox.getMessages()));
        } catch (MessagingException ex) {
            throw new RuntimeException("Exception retrieving mail with parameters: " + params, ex);
        }
    }

    private String getBody(Message msg) {
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

    public Date getSentAfter() {
        return sentAfter;
    }

    public void setSentAfter(Date sentAfter) {
        this.sentAfter = sentAfter;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }

    public static class SearchParameters {
        private String subject;
        private String receiver;
        private Date receivedAfterDate;

        public SearchParameters(String subject, String receiver, Date receivedAfterDate) {
            this.subject = subject;
            this.receiver = receiver;
            this.receivedAfterDate = receivedAfterDate;
        }

        private SearchTerm getSearchTerm() {
            return new SearchTerm() {
                private final static long serialVersionUID = -2333952189183496834L;

                @Override
                public boolean match(Message message) {
                    try {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Evaluating message with subject: {}, received: {}, to: {}",
                                    message.getSubject(), message.getReceivedDate(), getRecipient(message));
                        }
                        return message.getSubject().contains(subject)
                                && message.getReceivedDate().after(receivedAfterDate)
                                && getRecipient(message).contains(receiver);
                    } catch (MessagingException ex) {
                        throw new IllegalStateException("No match, message not found.." + ex.getMessage(), ex);
                    }
                }
            };
        }

        private static String getRecipient(Message message) throws MessagingException {
            return message.getRecipients(Message.RecipientType.TO)[0].toString();
        }

        @Override
        public String toString() {
            return format("subject='%s', receiver='%s', receivedAfterDate='%s'", subject, receiver, receivedAfterDate);
        }
    }
}
