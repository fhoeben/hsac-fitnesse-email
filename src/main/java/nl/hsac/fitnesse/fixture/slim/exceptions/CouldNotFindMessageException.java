package nl.hsac.fitnesse.fixture.slim.exceptions;

import nl.hsac.fitnesse.fixture.slim.EmailFixture;
import nl.hsac.fitnesse.fixture.slim.SlimFixtureException;

import javax.mail.Folder;

import static java.lang.String.format;

public class CouldNotFindMessageException extends SlimFixtureException {
    public CouldNotFindMessageException(Folder folder, EmailFixture.SearchParameters params) {
        super(false, format("Could not find mail in '%s' folder with search parameters: %s", folder.getName(), params.toString()));
    }
}
