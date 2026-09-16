-- Rates of the regulated passbooks, as set by the Ministry of the Economy.
--
-- Stored with their effective date rather than as one current value. A change is announced weeks
-- before it applies, so it can be merged ahead of time and take effect on the day by itself; and
-- the table doubles as a record of what applied when.
--
-- A new rate is a new migration, one row per product. That puts the change through review and ties
-- it to a commit, which an edited config value would not.

CREATE TABLE regulated_rate (
    account_type   VARCHAR(20)  NOT NULL CHECK (account_type IN ('LIVRET_A', 'LDDS')),
    effective_from DATE         NOT NULL,
    rate           NUMERIC(7,4) NOT NULL CHECK (rate >= 0),
    PRIMARY KEY (account_type, effective_from)
);

-- The LDDS has always carried the same rate as the Livret A.
INSERT INTO regulated_rate (account_type, effective_from, rate) VALUES
    ('LIVRET_A', DATE '2023-02-01', 3.0000),
    ('LDDS',     DATE '2023-02-01', 3.0000),
    ('LIVRET_A', DATE '2025-02-01', 2.4000),
    ('LDDS',     DATE '2025-02-01', 2.4000),
    ('LIVRET_A', DATE '2025-08-01', 1.7000),
    ('LDDS',     DATE '2025-08-01', 1.7000),
    ('LIVRET_A', DATE '2026-02-01', 1.5000),
    ('LDDS',     DATE '2026-02-01', 1.5000),
    ('LIVRET_A', DATE '2026-08-01', 1.7000),
    ('LDDS',     DATE '2026-08-01', 1.7000);
