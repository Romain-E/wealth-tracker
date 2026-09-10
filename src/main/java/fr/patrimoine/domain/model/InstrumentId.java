package fr.patrimoine.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Identity of a tradable instrument: an ISIN ({@code FR0000120271}), a ticker ({@code TTE.PA}) or a
 * provider-specific asset id ({@code BITCOIN}).
 *
 * <p>Normalised to upper case so that {@code btc} and {@code BTC} are the same key in a quote map.
 * Provider adapters that need a different casing &mdash; CoinGecko wants lower case &mdash; convert
 * on their own edge, which is where provider quirks belong.
 */
public record InstrumentId(String value) implements Comparable<InstrumentId> {

    private static final int MAX_LENGTH = 32;

    public InstrumentId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("An instrument id cannot be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Instrument id is too long: " + value);
        }
    }

    public static InstrumentId of(String value) {
        return new InstrumentId(value);
    }

    @Override
    public int compareTo(InstrumentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
