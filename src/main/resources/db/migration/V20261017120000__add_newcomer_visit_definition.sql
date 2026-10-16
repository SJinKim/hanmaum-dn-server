-- HDN-312: pin each quick record to the attendance definition of its day. Without the column the
-- owner was derived from which definitions are active *today*, so deactivating or replacing a
-- definition moved or dropped past visits, including days nobody checked in.
ALTER TABLE newcomer_visits ADD COLUMN definition_id BIGINT;
ALTER TABLE newcomer_visits
    ADD CONSTRAINT fk_newcomer_visits_definition
    FOREIGN KEY (definition_id) REFERENCES attendance_definitions(id) ON DELETE SET NULL;
CREATE INDEX idx_newcomer_visits_definition_date ON newcomer_visits (definition_id, visit_date);

-- Existing rows: the earliest definition held that day (an accepted check-in), otherwise the
-- earliest active one of that weekday. The same rule the service applies to new visits.
UPDATE newcomer_visits AS visit
SET definition_id = COALESCE(
    (
        SELECT definition.id
        FROM attendance_definitions AS definition
        WHERE definition.deleted_at IS NULL
          AND definition.day_of_week =
              (ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'])
                  [EXTRACT(ISODOW FROM visit.visit_date)::INT]
          AND EXISTS (
              SELECT 1
              FROM attendance_logs AS attendance_log
              WHERE attendance_log.definition_id = definition.id
                AND attendance_log.attendance_date = visit.visit_date
                AND attendance_log.attended = TRUE
                AND attendance_log.deleted_at IS NULL
          )
        ORDER BY definition.window_start, definition.id
        LIMIT 1
    ),
    (
        SELECT definition.id
        FROM attendance_definitions AS definition
        WHERE definition.deleted_at IS NULL
          AND definition.is_active = TRUE
          AND definition.day_of_week =
              (ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'])
                  [EXTRACT(ISODOW FROM visit.visit_date)::INT]
        ORDER BY definition.window_start, definition.id
        LIMIT 1
    )
)
WHERE visit.definition_id IS NULL;
