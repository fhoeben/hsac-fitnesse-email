package nl.hsac.fitnesse.fixture.util.mail;

import org.apache.commons.io.IOUtils;

import javax.mail.MessagingException;
import javax.mail.Part;
import java.io.IOException;
import java.io.InputStream;

public class ImapAttachment {
    private InputStream stream;
    private String fileName;

    public ImapAttachment(Part p) throws IOException, MessagingException {
        this.stream = p.getInputStream();
        this.fileName = p.getFileName();
    }

    public String getFileName() { return this.fileName; }
    public InputStream getStream() { return this.stream; }

    public byte[] getBytes() throws IOException  {return IOUtils.toByteArray(this.stream); }
}
