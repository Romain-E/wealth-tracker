-- Demo portfolio: a French saver in their thirties, with one envelope of each kind.
--
-- Loaded only by the `demo` profile (application-demo.yml). A repeatable migration rather than a
-- versioned one: Flyway re-applies it after the schema migrations whenever this file changes, so
-- the seed evolves in the same pull request as the schema it depends on instead of piling up as
-- V1000, V1001 and so on. It is written to be re-run: it replaces the demo accounts, identified by
-- fixed ids, and touches nothing else.
--
-- Dates are relative to the day the database is created, so a fresh environment always shows a
-- recent history. Amounts are computed by hand, which means they bypass every rule the Account
-- aggregate enforces. DemoSeedIT puts the rules back: it replays each ledger through the domain
-- and fails if a balance, a payment counter or a weighted average cost disagrees, or if a movement
-- is illegal (over a ceiling, ineligible instrument, not enough cash).
--
-- Quote prices are illustrative, not market data.

DO $$
DECLARE
    today         CONSTANT date := CURRENT_DATE;
    y             CONSTANT int  := extract(year FROM CURRENT_DATE)::int;

    livret_a      CONSTANT uuid := '00000000-0000-4000-a000-000000000001';
    ldds          CONSTANT uuid := '00000000-0000-4000-a000-000000000002';
    pea           CONSTANT uuid := '00000000-0000-4000-a000-000000000003';
    cto           CONSTANT uuid := '00000000-0000-4000-a000-000000000004';
    crypto        CONSTANT uuid := '00000000-0000-4000-a000-000000000005';
    assurance_vie CONSTANT uuid := '00000000-0000-4000-a000-000000000006';
    flat          CONSTANT uuid := '00000000-0000-4000-a000-000000000007';

    demo_accounts CONSTANT uuid[] := ARRAY[livret_a, ldds, pea, cto, crypto, assurance_vie, flat];
BEGIN
    DELETE FROM valuation           WHERE account_id = ANY (demo_accounts);
    DELETE FROM account_transaction WHERE account_id = ANY (demo_accounts);
    DELETE FROM account             WHERE id = ANY (demo_accounts); -- positions cascade

    -- ------------------------------------------------------------------------------- accounts
    -- Every balance and payment counter below is the sum of that account's movements.
    INSERT INTO account (id, label, type, currency, opened_on,
                         cash_balance, net_payments, gross_payments, version)
    VALUES
        (livret_a,      'Livret A',         'LIVRET_A',      'EUR', make_date(y - 8, 1, 15),
                         23390.95,  22450.00,  23950.00, 0),
        (ldds,          'LDDS',             'LDDS',          'EUR', make_date(y - 5, 9, 1),
                          8660.40,   8500.00,   8500.00, 0),
        (pea,           'PEA',              'PEA',           'EUR', make_date(y - 7, 5, 2),
                          2190.30,  45000.00,  45000.00, 0),
        (cto,           'Compte-titres',    'CTO',           'EUR', make_date(y - 3, 2, 1),
                          1986.01,   8000.00,   8000.00, 0),
        (crypto,        'Crypto',           'CRYPTO',        'EUR', make_date(y - 2, 11, 20),
                           387.00,   3000.00,   3000.00, 0),
        (assurance_vie, 'Assurance vie',    'ASSURANCE_VIE', 'EUR', make_date(y - 6, 10, 1),
                         15000.00,  15000.00,  15000.00, 0),
        (flat,          'Appartement Lyon', 'REAL_ESTATE',   'EUR', make_date(y - 4, 7, 1),
                        280000.00, 280000.00, 280000.00, 0);

    -- ------------------------------------------------------------------------------ positions
    -- Weighted average cost, fees included, as the French tax rules require:
    --   LU1681043599  50 @ 300.00 + 4.50, 30 @ 380.00 + 5.70, 20 @ 540.00 + 10.80
    --                 = 37 221.00 for 100 units -> 372.21
    --   FR0000120073  20 @ 140.00 + 2.80 = 2 802.80 -> 140.14
    --   FR0000120271  100 @ 55.00 + 5.50 = 5 505.50 -> 55.055; 40 sold, average unchanged
    --   IE00B4L5Y983  80 @ 75.00 + 1.99 = 6 001.99 -> 75.024875
    --   BITCOIN       0.05 @ 36 000.00 + 9.00 = 1 809.00 -> 36 180
    --   ETHEREUM      0.4 @ 2 000.00 + 4.00 = 804.00 -> 2 010
    INSERT INTO account_position (account_id, instrument_id, kind, quantity, average_cost, cost_currency)
    VALUES
        (pea,    'LU1681043599', 'ETF',    100,  372.21,    'EUR'),
        (pea,    'FR0000120073', 'EQUITY', 20,   140.14,    'EUR'),
        (pea,    'FR0000120271', 'EQUITY', 60,   55.055,    'EUR'),
        (cto,    'IE00B4L5Y983', 'ETF',    80,   75.024875, 'EUR'),
        (crypto, 'BITCOIN',      'CRYPTO', 0.05, 36180,     'EUR'),
        (crypto, 'ETHEREUM',     'CRYPTO', 0.4,  2010,      'EUR');

    -- --------------------------------------------------------------------------------- ledger
    INSERT INTO account_transaction (id, account_id, type, trade_date, amount, currency,
                                     instrument_id, quantity, unit_price, price_currency)
    VALUES
        -- Livret A: paid up to the 22 950 ceiling, then interest carries the balance above it,
        -- which is legal. A withdrawal frees room that a later deposit partly uses again.
        (gen_random_uuid(), livret_a, 'DEPOSIT',    make_date(y - 8, 1, 15),  10000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), livret_a, 'DEPOSIT',    make_date(y - 6, 6, 10),  12950.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), livret_a, 'INTEREST',   make_date(y - 2, 12, 31),   550.80, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), livret_a, 'INTEREST',   make_date(y - 1, 12, 31),   390.15, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), livret_a, 'WITHDRAWAL', today - 120,              -1500.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), livret_a, 'DEPOSIT',    today - 40,                1000.00, 'EUR', NULL, NULL, NULL, NULL),

        -- LDDS
        (gen_random_uuid(), ldds, 'DEPOSIT',  make_date(y - 5, 9, 1),   5000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), ldds, 'DEPOSIT',  make_date(y - 3, 3, 15),  3000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), ldds, 'INTEREST', make_date(y - 1, 12, 31),  160.40, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), ldds, 'DEPOSIT',  today - 75,                500.00, 'EUR', NULL, NULL, NULL, NULL),

        -- PEA. A trade's amount is its all-in cash effect: units times price, plus fees on a
        -- purchase, minus fees on a sale.
        (gen_random_uuid(), pea, 'DEPOSIT',  make_date(y - 7, 5, 2),   20000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), pea, 'BUY',      make_date(y - 7, 5, 10), -15004.50, 'EUR', 'LU1681043599', 50,  300.00, 'EUR'),
        (gen_random_uuid(), pea, 'BUY',      make_date(y - 6, 11, 12), -2802.80, 'EUR', 'FR0000120073', 20,  140.00, 'EUR'),
        (gen_random_uuid(), pea, 'DEPOSIT',  make_date(y - 4, 1, 10),  15000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), pea, 'BUY',      make_date(y - 4, 1, 15), -11405.70, 'EUR', 'LU1681043599', 30,  380.00, 'EUR'),
        (gen_random_uuid(), pea, 'BUY',      make_date(y - 3, 6, 20),  -5505.50, 'EUR', 'FR0000120271', 100, 55.00,  'EUR'),
        (gen_random_uuid(), pea, 'DIVIDEND', make_date(y - 1, 5, 20),    322.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), pea, 'SELL',     make_date(y - 1, 9, 15),   2397.60, 'EUR', 'FR0000120271', 40,  60.00,  'EUR'),
        (gen_random_uuid(), pea, 'DEPOSIT',  today - 60,               10000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), pea, 'BUY',      today - 55,              -10810.80, 'EUR', 'LU1681043599', 20,  540.00, 'EUR'),

        -- Compte-titres
        (gen_random_uuid(), cto, 'DEPOSIT', make_date(y - 3, 2, 1),    8000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), cto, 'BUY',     make_date(y - 3, 2, 3),   -6001.99, 'EUR', 'IE00B4L5Y983', 80, 75.00, 'EUR'),
        (gen_random_uuid(), cto, 'FEE',     make_date(y - 1, 12, 31),   -12.00, 'EUR', NULL, NULL, NULL, NULL),

        -- Crypto
        (gen_random_uuid(), crypto, 'DEPOSIT', make_date(y - 2, 11, 20),  3000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), crypto, 'BUY',     make_date(y - 2, 11, 21), -1809.00, 'EUR', 'BITCOIN',  0.05, 36000.00, 'EUR'),
        (gen_random_uuid(), crypto, 'BUY',     make_date(y - 1, 3, 4),    -804.00, 'EUR', 'ETHEREUM', 0.4,  2000.00,  'EUR'),

        -- Assurance vie and the flat are valued by snapshot; their deposits are what was put in.
        (gen_random_uuid(), assurance_vie, 'DEPOSIT', make_date(y - 6, 10, 1),  10000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), assurance_vie, 'DEPOSIT', make_date(y - 2, 10, 1),   5000.00, 'EUR', NULL, NULL, NULL, NULL),
        (gen_random_uuid(), flat,          'DEPOSIT', make_date(y - 4, 7, 1),  280000.00, 'EUR', NULL, NULL, NULL, NULL);

    -- --------------------------------------------------------------------------------- quotes
    -- ON CONFLICT DO NOTHING: a price fetched for real since the seed ran is newer than these.
    INSERT INTO quote (instrument_id, price, currency, as_of)
    VALUES
        ('LU1681043599',   560.00, 'EUR', now() - interval '1 day'),
        ('FR0000120073',   175.00, 'EUR', now() - interval '1 day'),
        ('FR0000120271',    56.00, 'EUR', now() - interval '1 day'),
        ('IE00B4L5Y983',   105.00, 'EUR', now() - interval '1 day'),
        ('BITCOIN',      80000.00, 'EUR', now() - interval '1 day'),
        ('ETHEREUM',      2500.00, 'EUR', now() - interval '1 day')
    ON CONFLICT (instrument_id) DO NOTHING;

    -- ----------------------------------------------------------------------------- valuations
    -- Snapshots, on their own sparse calendars: insurer statements once a year, the flat
    -- appraised when it was bought and then roughly yearly.
    INSERT INTO valuation (account_id, valuation_date, amount, currency, source)
    VALUES
        (assurance_vie, make_date(y - 5, 12, 31),  10150.00, 'EUR', 'MANUAL'),
        (assurance_vie, make_date(y - 4, 12, 31),  10420.00, 'EUR', 'MANUAL'),
        (assurance_vie, make_date(y - 3, 12, 31),  10610.00, 'EUR', 'MANUAL'),
        (assurance_vie, make_date(y - 2, 12, 31),  15880.00, 'EUR', 'MANUAL'),
        (assurance_vie, make_date(y - 1, 12, 31),  16350.00, 'EUR', 'MANUAL'),
        (flat,          make_date(y - 4, 7, 1),   280000.00, 'EUR', 'MANUAL'),
        (flat,          make_date(y - 2, 6, 30),  295000.00, 'EUR', 'MANUAL'),
        (flat,          make_date(y - 1, 6, 30),  305000.00, 'EUR', 'MANUAL');

    -- Month-end history for the envelopes the application values itself, derived from the ledger
    -- so the curve steps up on the days money was actually paid in. Regulated savings are worth
    -- their balance. Market envelopes are worth what was paid in, grown at an illustrative annual
    -- rate with a gentle oscillation so the chart does not look like a ruler.
    INSERT INTO valuation (account_id, valuation_date, amount, currency, source)
    SELECT a.id,
           m.month_end,
           CASE WHEN a.type IN ('LIVRET_A', 'LDDS')
                THEN ledger.balance
                ELSE round((ledger.paid_in
                            * (1 + g.annual_growth * (m.month_end - a.opened_on) / 365.25)
                            * (1 + g.swing * sin(m.n)))::numeric, 2)
           END,
           a.currency,
           'COMPUTED'
    FROM account a
    JOIN (VALUES (livret_a, 0.00, 0.00),
                 (ldds,     0.00, 0.00),
                 (pea,      0.07, 0.03),
                 (cto,      0.06, 0.03),
                 (crypto,   0.15, 0.12)) AS g (account_id, annual_growth, swing)
      ON g.account_id = a.id
    CROSS JOIN LATERAL (
        SELECT s.n,
               (date_trunc('month', today) - make_interval(months => s.n) - interval '1 day')::date
                   AS month_end
        FROM generate_series(0, 23) AS s (n)
    ) m
    CROSS JOIN LATERAL (
        SELECT coalesce(sum(t.amount), 0) AS balance,
               coalesce(sum(t.amount) FILTER (WHERE t.type IN ('DEPOSIT', 'WITHDRAWAL')), 0) AS paid_in
        FROM account_transaction t
        WHERE t.account_id = a.id
          AND t.trade_date <= m.month_end
    ) ledger
    WHERE m.month_end >= a.opened_on;
END
$$;
