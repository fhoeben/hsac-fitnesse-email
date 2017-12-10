package nl.hsac.fitnesse.fixture.slim;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EmailFixtureTest {
    private EmailFixture fixture = new EmailFixture();

    @Before
    public void setUp() {
        fixture.connectToHostWithUserAndPassword("imap.gmail.com", "sample@hsac.nl", getPassword());
    }

    @Test
    public void testLastMessage() {
        fixture.onlyMessagesReceivedAfter("2017-12-09 00:00:00");
        fixture.onlyMessagesSentTo("sample+check2@hsac.nl");
        fixture.onlyMessagesWithSubject("Testje");
        boolean found = fixture.retrieveLastMessage();

        assertTrue("Expected at least one message", found);
        assertNotNull("No subject", fixture.subject());
        assertNotNull("No sender", fixture.sender());
        assertNotNull("No sent date", fixture.sentDate());
        assertNotNull("No received date", fixture.receivedDate());
        assertNotNull("No toRecipient", fixture.toRecipient());
        assertNotNull("No bodyText", fixture.bodyText());
        assertNotNull("No body", fixture.body());

        assertEquals(fixture.getExpectedSubject(), fixture.subject());
        assertEquals(fixture.getExpectedTo(), fixture.toRecipient());
        assertEquals("Hallo test2\r\n\r\n", fixture.bodyText());
    }

    @Test
    public void testRetrieveMessagesUntilMatchFound() {
        fixture.setRepeatIntervalToMilliseconds(1000);
        fixture.repeatAtMostTimes(2);
        fixture.onlyMessagesReceivedAfter(null);
        fixture.onlyMessagesSentTo(null);
        fixture.onlyMessagesWithSubject("Testje");
        boolean found = fixture.retrieveUntilMessageFound();

        assertTrue("Expected at least one message", found);

        assertEquals("Expected message to be available immediately", 0, fixture.repeatCount());
        assertEquals(fixture.getExpectedSubject(), fixture.subject());
        assertEquals("Andere test\r\n", fixture.bodyText());
    }

    @Test
    public void testRetrieveMessagesUntilMatchFoundNoneFound() {
        fixture.setRepeatIntervalToMilliseconds(60);
        fixture.repeatAtMostTimes(2);
        fixture.onlyMessagesSentTo("asdasdad");
        boolean found = fixture.retrieveUntilMessageFound();

        assertEquals("Expected repeat count", 2, fixture.repeatCount());
        assertFalse("Expected no message", found);

        // ensure no exceptions thrown
        assertNull("subject", fixture.subject());
        assertNull("sender", fixture.sender());
        assertNull("sent date", fixture.sentDate());
        assertNull("received date", fixture.receivedDate());
        assertNull("toRecipient", fixture.toRecipient());
        assertNull("bodyText", fixture.bodyText());
        assertNull("body", fixture.body());
    }

    @Test
    public void testWaitUntilMatchFoundNoneFound() {
        fixture.setRepeatIntervalToMilliseconds(50);
        fixture.repeatAtMostTimes(3);
        fixture.onlyMessagesSentTo("assadasdgagsjddasdad");
        try {
            boolean found = fixture.waitUntilMessageFound();
            fail("Expected exception got: " + fixture.subject());
        } catch (StopTestException e) {
            String message = e.getMessage();
            assertNotNull(message);
            assertTrue("Bad message: " + message,
                        message.startsWith("message:<<No message found matching criteria. Tried: 3 times, for: "));
        }
    }

    private String getPassword() {
        return System.getProperty("emailPassword");
    }

}