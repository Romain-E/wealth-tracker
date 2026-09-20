package fr.patrimoine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.patrimoine.domain.model.InstrumentId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceSlice
@DisplayName("JdbcInstrumentSymbolStore")
class JdbcInstrumentSymbolStoreIT {

    private static final InstrumentId MSCI_WORLD = InstrumentId.of("IE00B4L5Y983");
    private static final InstrumentId AIR_LIQUIDE = InstrumentId.of("FR0000120073");

    @Autowired private JdbcInstrumentSymbolStore symbols;

    @Test
    @DisplayName("ships the curated euro listing for a fund whose search only finds dollars")
    void providesCuratedListings() {
        assertThat(symbols.find(MSCI_WORLD, "YAHOO")).contains("EUNL.DE");
    }

    @Test
    @DisplayName("remembers a resolved symbol, and never lets one overwrite a curated row")
    void remembersResolvedSymbolsWithoutOverridingCuratedOnes() {
        symbols.remember(AIR_LIQUIDE, "YAHOO", "AI.PA");
        symbols.remember(MSCI_WORLD, "YAHOO", "IWDA.L");

        assertThat(symbols.find(AIR_LIQUIDE, "YAHOO")).contains("AI.PA");
        assertThat(symbols.find(MSCI_WORLD, "YAHOO")).contains("EUNL.DE");
        assertThat(symbols.find(AIR_LIQUIDE, "OTHER")).isEmpty();
    }
}
