-- HDN-0: Seed the training catalog with the three standard trainings
--
-- Idempotent: WHERE NOT EXISTS guards prevent duplicate rows on re-run.
-- sort_order encodes the progression QTBS (1) -> 1on1 (2) -> Discipleship (3).

INSERT INTO training (name, sort_order)
SELECT t.name, t.sort_order
FROM (VALUES
    ('QTBS', 1),
    ('1on1', 2),
    ('Discipleship', 3)
) AS t(name, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM training x WHERE x.name = t.name
);
