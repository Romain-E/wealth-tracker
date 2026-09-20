package fr.patrimoine.infrastructure.persistence;

import fr.patrimoine.domain.model.InstrumentId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * How each quote provider names an instrument that this application identifies by ISIN.
 *
 * <p>Two kinds of rows live here. Curated ones come from a migration and exist because an automatic
 * lookup would pick the wrong listing: the same fund quoted in dollars in London instead of in
 * euros in Frankfurt. Resolved ones are remembered after a successful search, so the search runs
 * once per instrument rather than once per page view. A resolved row never replaces a curated one.
 */
@Repository
public class JdbcInstrumentSymbolStore {

    private final JdbcClient jdbc;

    public JdbcInstrumentSymbolStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> find(InstrumentId instrument, String provider) {
        return jdbc.sql(
                        """
                        SELECT symbol FROM instrument_symbol
                        WHERE instrument_id = :instrument AND provider = :provider
                        """)
                .param("instrument", instrument.value())
                .param("provider", provider)
                .query(String.class)
                .optional();
    }

    public void remember(InstrumentId instrument, String provider, String symbol) {
        jdbc.sql(
                        """
                        INSERT INTO instrument_symbol (instrument_id, provider, symbol, curated)
                        VALUES (:instrument, :provider, :symbol, FALSE)
                        ON CONFLICT (instrument_id, provider) DO NOTHING
                        """)
                .param("instrument", instrument.value())
                .param("provider", provider)
                .param("symbol", symbol)
                .update();
    }
}
