package fr.patrimoine.infrastructure.quote;

import fr.patrimoine.application.port.out.AccountRepository;
import fr.patrimoine.domain.model.InstrumentId;
import fr.patrimoine.domain.model.InstrumentKind;
import fr.patrimoine.domain.model.Position;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the price cache warm, so that a page view rarely waits on a provider.
 *
 * <p>It runs more often than cached prices expire, so an instrument that is actually held is almost
 * always served from the cache. Requests then only reach a provider for an instrument bought since
 * the last refresh, which keeps the slow, unreliable call off the path a user is waiting on.
 *
 * <p>The held instruments are read in a short transaction that ends before any network call. A
 * provider taking seconds to answer must not keep a database connection checked out meanwhile.
 */
@Component
class QuoteRefresher {

    private final AccountRepository accounts;
    private final LiveQuoteRepository quotes;
    private final TransactionTemplate readOnly;
    private final QuoteProperties properties;

    QuoteRefresher(
            AccountRepository accounts,
            LiveQuoteRepository quotes,
            PlatformTransactionManager transactions,
            QuoteProperties properties) {
        this.accounts = accounts;
        this.quotes = quotes;
        this.properties = properties;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @Scheduled(
            initialDelayString = "${patrimoine.quotes.refresh-initial-delay}",
            fixedDelayString = "${patrimoine.quotes.refresh-interval}")
    void refresh() {
        if (!properties.livePrices()) {
            return;
        }
        Map<InstrumentId, InstrumentKind> held = readOnly.execute(status -> heldInstruments());
        if (held != null && !held.isEmpty()) {
            quotes.refresh(held);
        }
    }

    private Map<InstrumentId, InstrumentKind> heldInstruments() {
        return accounts.findAll().stream()
                .flatMap(account -> account.positions().stream())
                .collect(
                        Collectors.toMap(
                                Position::instrument, Position::kind, (first, second) -> first));
    }
}
