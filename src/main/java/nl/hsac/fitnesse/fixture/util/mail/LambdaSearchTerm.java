package nl.hsac.fitnesse.fixture.util.mail;

import javax.mail.Message;
import javax.mail.search.SearchTerm;
import java.util.function.Predicate;

/**
 *
 */
public class LambdaSearchTerm extends SearchTerm {
    private final Predicate<Message> predicate;

    public LambdaSearchTerm(Predicate<Message> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean match(Message msg) {
        return predicate.test(msg);
    }
}
