-- The wealth ledger.
--
-- Column scales mirror the domain types: amounts are NUMERIC(19,2) like Money, prices and
-- quantities NUMERIC(28,8) like Price and Quantity. The domain decides the precision; the schema's
-- job is never to lose it.
--
-- The CHECK constraints repeat invariants the aggregate already enforces, deliberately. The
-- aggregate guards every write made through the application, but a seed script, a fix typed into
-- psql or a future import job does not go through the application. Only the database sees every
-- write.

CREATE TABLE account (
    id             UUID          PRIMARY KEY,
    label          VARCHAR(100)  NOT NULL CHECK (btrim(label) <> ''),
    type           VARCHAR(20)   NOT NULL CHECK (type IN ('LIVRET_A', 'LDDS', 'PEA', 'CTO', 'CRYPTO',
                                                          'ASSURANCE_VIE', 'REAL_ESTATE')),
    currency       VARCHAR(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    opened_on      DATE          NOT NULL,
    cash_balance   NUMERIC(19,2) NOT NULL CHECK (cash_balance >= 0),
    -- No ceiling check here. Ceilings are law and change; and they constrain deposits, not
    -- balances, so a legal account can sit above one (see Account.rehydrate).
    net_payments   NUMERIC(19,2) NOT NULL CHECK (net_payments >= 0),
    gross_payments NUMERIC(19,2) NOT NULL,
    -- Optimistic lock: see JpaAccountRepository for why a regulatory ceiling needs one.
    version        BIGINT        NOT NULL,
    CONSTRAINT account_net_payments_within_gross CHECK (net_payments <= gross_payments)
);

-- Positions are values inside the account aggregate, not entities: they have no identity beyond
-- (account, instrument), and they go when the account goes.
CREATE TABLE account_position (
    account_id    UUID          NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    instrument_id VARCHAR(32)   NOT NULL CHECK (instrument_id = upper(btrim(instrument_id))),
    kind          VARCHAR(10)   NOT NULL CHECK (kind IN ('EQUITY', 'ETF', 'FUND', 'CRYPTO')),
    quantity      NUMERIC(28,8) NOT NULL CHECK (quantity > 0),
    average_cost  NUMERIC(28,8) NOT NULL CHECK (average_cost >= 0),
    cost_currency VARCHAR(3)    NOT NULL CHECK (cost_currency ~ '^[A-Z]{3}$'),
    PRIMARY KEY (account_id, instrument_id)
);

-- The ledger. Append-only: nothing in the application updates or deletes a movement.
CREATE TABLE account_transaction (
    id              UUID          PRIMARY KEY,
    -- Insertion order, to break ties between movements on the same day (a deposit, then the
    -- purchase it funds). Random UUIDs have no order to offer.
    sequence_number BIGINT        GENERATED ALWAYS AS IDENTITY UNIQUE,
    account_id      UUID          NOT NULL REFERENCES account (id),
    type            VARCHAR(12)   NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY', 'SELL',
                                                           'DIVIDEND', 'INTEREST', 'FEE', 'TAX')),
    trade_date      DATE          NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    instrument_id   VARCHAR(32)   CHECK (instrument_id = upper(btrim(instrument_id))),
    quantity        NUMERIC(28,8) CHECK (quantity > 0),
    unit_price      NUMERIC(28,8) CHECK (unit_price >= 0),
    price_currency  VARCHAR(3)    CHECK (price_currency ~ '^[A-Z]{3}$'),

    -- Trade details are present exactly when the movement is a trade.
    CONSTRAINT account_transaction_trade_fields CHECK (
        CASE WHEN type IN ('BUY', 'SELL')
             THEN instrument_id IS NOT NULL AND quantity IS NOT NULL
                  AND unit_price IS NOT NULL AND price_currency IS NOT NULL
             ELSE instrument_id IS NULL AND quantity IS NULL
                  AND unit_price IS NULL AND price_currency IS NULL
        END),

    -- Amounts are signed from the account's point of view, and the return calculation reads the
    -- sign as the direction of the cash flow. A withdrawal stored as positive would be counted as
    -- a deposit and silently corrupt every performance figure.
    CONSTRAINT account_transaction_amount_sign CHECK (
        CASE WHEN type IN ('DEPOSIT', 'DIVIDEND', 'INTEREST') THEN amount > 0
             WHEN type IN ('WITHDRAWAL', 'FEE', 'TAX')         THEN amount < 0
             WHEN type = 'BUY'                                 THEN amount <= 0
             ELSE amount >= 0
        END)
);

-- Serves both "one account's history" and "these accounts since 1 January".
CREATE INDEX account_transaction_account_date_idx ON account_transaction (account_id, trade_date);

-- One value per account per day: a valuation recomputed later the same day replaces the earlier
-- one. The primary key makes that a fact rather than a convention.
CREATE TABLE valuation (
    account_id     UUID          NOT NULL REFERENCES account (id),
    valuation_date DATE          NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    currency       VARCHAR(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    source         VARCHAR(10)   NOT NULL CHECK (source IN ('MANUAL', 'COMPUTED', 'IMPORTED')),
    PRIMARY KEY (account_id, valuation_date)
);

-- The primary key serves every per-account lookup. The consolidated chart filters on the date
-- alone, which a key led by account_id cannot serve.
CREATE INDEX valuation_date_idx ON valuation (valuation_date);

-- Last known price per instrument. as_of is when the price was observed upstream, not when the
-- row was written.
CREATE TABLE quote (
    instrument_id VARCHAR(32)   PRIMARY KEY CHECK (instrument_id = upper(btrim(instrument_id))),
    price         NUMERIC(28,8) NOT NULL CHECK (price >= 0),
    currency      VARCHAR(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    as_of         TIMESTAMPTZ   NOT NULL
);
