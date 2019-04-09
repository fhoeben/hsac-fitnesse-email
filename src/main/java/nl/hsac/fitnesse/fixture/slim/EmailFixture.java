package nl.hsac.fitnesse.fixture.slim;

import nl.hsac.fitnesse.fixture.util.ThrowingFunction;
import nl.hsac.fitnesse.fixture.util.mail.ImapAttachment;
import org.apache.commons.lang3.StringUtils;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.AndTerm;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.FlagTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SearchTerm;
import javax.mail.search.SubjectTerm;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Fixture to check for mails received in imap mailbox.
 */
public class EmailFixture extends SlimFixture {
    private String attachmentBase = new File(filesDir, "attachment").getPath() + "/";
    protected static final DateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected static final FlagTerm NON_DELETED_TERM = new FlagTerm(new Flags(Flags.Flag.DELETED), false);
    private String folder = "inbox";
    private Folder currentFolder = null;
    private Store store;
    private Message lastMessage;
    private int folderReadWrite = Folder.READ_ONLY;

    private Date receivedAfter = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24);
    private String expectedTo;
    private String expectedSubject;

    private String messagePlain;
    private String messageHtml;
    private List<ImapAttachment> messageAttachments = new ArrayList();

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
    public void connectToHostWithUserAndPasswordWithWrite(String host, String username, String password) {
       connectToHostWithUserAndPassword(host,username,password);
       folderReadWrite = Folder.READ_WRITE;
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

    public ArrayList<String> attachmentNames() {
        ArrayList<String> values = null;
        if (messageAttachments.size() > 0) {
            values = new ArrayList<>();
            for (ImapAttachment ia : messageAttachments) {
                values.add(ia.getFileName());
            }
        }
        return values;
    }

    public String saveAttachmentNumber(int number)
    {
        if (number < messageAttachments.size()) {
            return impSaveAttachment(messageAttachments.get(number));
        }
        else {
            throw new SlimFixtureException(false, "There are only "+ messageAttachments.size() + " attachments");
        }
    }

    public String saveAttachmentNamed(String filename)
    {
        for (ImapAttachment ia: messageAttachments) {
            if (StringUtils.equals(ia.getFileName(), filename)) {
                return impSaveAttachment(ia);
            }
        }
        throw new SlimFixtureException(false, "There is no attachment called " + filename);
    }
    private String impSaveAttachment(ImapAttachment ia) {
        try {
            return createFile(attachmentBase, ia.getFileName(), ia.getBytes());
        } catch (IOException ex) {
            throw new SlimFixtureException("Unable to extract: " + ia.getFileName(), ex);
        }
    }

    public String body() {
        String result = bodyText();
        return getEnvironment().getHtml(result);
    }

    public String bodyText() {
        return messageHtml;
    }

    public String bodyPlain() {
        return messagePlain;
    }

    private void handleParts(Part part) {
        try {
            if (part == null) return;
            String disposition = part.getDisposition();
            if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) {
                messageAttachments.add(new ImapAttachment(part));
            } else {
                Object content = part.getContent();
                if (content instanceof MimeMultipart) {
                    Multipart multipart = (Multipart) content;
                    for (int i = 0; i < multipart.getCount(); i++) {
                        handleParts(multipart.getBodyPart(i));
                    }
                } else if (content instanceof String) {
                    if (part.isMimeType("text/html")) {
                        messageHtml = (String)content;
                    }
                    else {
                        messagePlain = (String)content;
                    }
                } else {
                    throw new SlimFixtureException(false, "Unknown content type " + content.getClass());
                }
            }
        } catch (IOException ex) {
            throw new SlimFixtureException("Unable to get body of message", ex);
        } catch (MessagingException ex) {
            throw new SlimFixtureException("Unable to get body of message", ex);
        }
    }

    public boolean retrieveLastMessage() {
        List<Message> messages = retrieveMessages();
        boolean result = (messages != null && messages.size() > 0);
        setLastMessage(result? messages.get(messages.size() - 1): null);
        return result;
    }

    protected List<Message> retrieveMessages() {
        SearchTerm searchTerm = getSearchTerm();
        try {
            openCurrentFolder();
            Message[] messages = currentFolder.search(searchTerm);
            if(messages.length == 0 ) { closeCurrentFolder(); return Collections.emptyList(); }
            if(receivedAfter == null) return Arrays.asList(messages);
            return Arrays.stream(messages).filter(x -> {
                try {
                    return x.getReceivedDate().after(receivedAfter);
                }
                catch (MessagingException ex) {
                    throw new RuntimeException("Unable to get received date", ex);
                }
            }).collect(Collectors.toList());
        } catch (MessagingException e) {
            throw new SlimFixtureException("Unable to retrieve messages", e);
        }
    }

    public boolean retrieveUntilMessageFound() {
        return repeatUntil(new FunctionalCompletion(this::retrieveLastMessage));
    }

    public boolean waitUntilMessageFound() {
        if (!retrieveUntilMessageFound()) {
            throw new StopTestException(false, "No message found matching criteria. Tried: " + repeatCount()
                    + " times, for: " + timeSpentRepeating() + "ms.");
        }
        return true;
    }

    protected SearchTerm getSearchTerm() {
        SearchTerm term = NON_DELETED_TERM;
        if (receivedAfter != null) {
            term = getReceivedAfterTerm(term);
        }
        if (expectedSubject != null) {
            term = new AndTerm(term, new SubjectTerm(expectedSubject));
        }
        if (expectedTo != null) {
            term = new AndTerm(term, new RecipientStringTerm(Message.RecipientType.TO, expectedTo));
        }
        return term;
    }

    protected SearchTerm getReceivedAfterTerm(SearchTerm term) {
        //Work around to IMAP not dealing with time part
        Calendar c = Calendar.getInstance();
        c.setTime(this.receivedAfter);
        c.add(Calendar.DATE, -1);
        return new AndTerm(term, new ReceivedDateTerm(ComparisonTerm.GT, c.getTime()));
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

    protected void setLastMessage(Message lastMessage) {
        messagePlain = messageHtml = "";
        messageAttachments.clear();
        this.lastMessage = lastMessage;
        handleParts(lastMessage);
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

    protected void openCurrentFolder() {
        try {
            if (currentFolder == null || !currentFolder.isOpen()) {
                currentFolder = store.getFolder(folder);
                currentFolder.open(folderReadWrite);
            }
        } catch (MessagingException e) {
            throw new StopTestException("Unable to open folder: " + folder);
        }
    }

    protected void closeCurrentFolder() {
        try {
            if (currentFolder != null && currentFolder.isOpen()) {
                currentFolder.close();
            }
        } catch (MessagingException e) {
            throw new StopTestException("Unable to close folder: " + folder);
        }
    }
    
    protected Folder getCurrentFolder() {
        return currentFolder;
    }

    protected String getBody(Message msg) {
        return messageHtml;
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
        if(StringUtils.equalsIgnoreCase(this.folder, folder) == false) {
            closeCurrentFolder();
        }
        this.folder = folder;
    }

    public String getFolder() {
        return folder;
    }

    public ArrayList<String> getFolders() {
        try {
            ArrayList<String> folders = new ArrayList<>();
            handleListFolders(this.store.getDefaultFolder(),"",folders);
            return folders;
        }
        catch (MessagingException ex) {
            throw new SlimFixtureException("Unable to get default folder", ex);
        }
    }

    private void handleListFolders(Folder folder, String base, List<String> folders) throws MessagingException  {
        String name = base + folder.getName();
        String b = name.isEmpty() ? "" : name + "/";
        if(!name.isEmpty()) folders.add(name);
        for(Folder f : folder.list()) {
            handleListFolders(f, b, folders);
        }
    }

    protected Store getStore() {
        return store;
    }

    public Boolean moveLastMessageToFolder(String folder ) {
        return moveMessage(lastMessage,folder, true);
    }

    protected Boolean moveMessage(Message message, String toFolderPath, boolean createIfNotExist) {
        try {
            Message[] messages = {message};
            Folder fromFolder = message.getFolder();
            Folder toFolder = fromFolder.getStore().getFolder(toFolderPath);
            if (!toFolder.exists() && createIfNotExist) toFolder.create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
            fromFolder.copyMessages(messages, toFolder);
            fromFolder.setFlags(messages, new Flags(Flags.Flag.DELETED), true);
            fromFolder.expunge();
            return true;
        } catch (MessagingException ex) {
            throw new SlimFixtureException("Unable to move the message", ex);
        }
    }
}
