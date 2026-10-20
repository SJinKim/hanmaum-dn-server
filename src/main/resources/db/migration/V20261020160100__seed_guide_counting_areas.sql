-- HDN-331: initial approved areas; A and B share the existing sanctuary.
DO $$
DECLARE area RECORD; matched_room BIGINT; matches INTEGER;
BEGIN
    FOR area IN SELECT * FROM (VALUES
        ('e3310000-0000-4000-8000-000000000001'::uuid, 1, '1-1 대예배실', '본당1층A', 0),
        ('e3310000-0000-4000-8000-000000000002'::uuid, 1, '1-1 대예배실', '본당1층B', 1),
        ('e3310000-0000-4000-8000-000000000003'::uuid, 2, '대예배실 2층', '본당2층C', 2),
        ('e3310000-0000-4000-8000-000000000004'::uuid, 1, '1-3 유아유치부실', '유아유치부', 3)
    ) AS initial(public_id, floor_number, room_name, label, display_order)
    LOOP
        SELECT count(*), min(r.id) INTO matches, matched_room FROM room r JOIN floor f ON f.id = r.floor_id
        WHERE f.floor_number = area.floor_number AND r.name = area.room_name AND r.deleted_at IS NULL AND f.deleted_at IS NULL;
        IF matches <> 1 THEN RAISE EXCEPTION 'HDN-331 requires an unambiguous existing room for each initial counting area'; END IF;
        INSERT INTO guide_counting_areas(public_id, room_id, label, display_order)
        VALUES(area.public_id, matched_room, area.label, area.display_order) ON CONFLICT (public_id) DO NOTHING;
    END LOOP;
END $$;
