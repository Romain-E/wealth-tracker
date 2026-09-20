-- How each quote provider names an instrument.
--
-- The application identifies securities by ISIN, which pins down the security but not the listing:
-- one fund trades in euros in Paris and Frankfurt and in dollars in London. Providers price
-- listings, so each ISIN needs a provider symbol.
--
-- Most rows are resolved automatically, by searching the provider and keeping a euro-area listing.
-- Curated rows cover the cases where that search cannot find one. They are reference data, reviewed
-- like code, and a resolved row never overwrites them.

CREATE TABLE instrument_symbol (
    instrument_id VARCHAR(32) NOT NULL CHECK (instrument_id = upper(btrim(instrument_id))),
    provider      VARCHAR(20) NOT NULL CHECK (provider IN ('YAHOO')),
    symbol        VARCHAR(32) NOT NULL CHECK (btrim(symbol) <> ''),
    curated       BOOLEAN     NOT NULL,
    PRIMARY KEY (instrument_id, provider)
);

-- iShares Core MSCI World. Searching its ISIN only returns the London listing, quoted in US
-- dollars; the Xetra listing is the same fund, in euros.
INSERT INTO instrument_symbol (instrument_id, provider, symbol, curated)
VALUES ('IE00B4L5Y983', 'YAHOO', 'EUNL.DE', TRUE);
