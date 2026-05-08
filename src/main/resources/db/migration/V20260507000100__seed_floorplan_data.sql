-- HDN-0: Seed 1F and 2F floor plan room data
--
-- Source: church floor plan SVG (viewBox 0 0 900 500 for both floors).
-- Points are normalized polygon vertices [[x,y],...] where each coordinate
-- is in [0.0, 1.0] relative to the 900×500 canvas.
-- Idempotent: IF NOT EXISTS / WHERE NOT EXISTS guards prevent duplicate runs.

DO $$
DECLARE
    v_f1 BIGINT;
    v_f2 BIGINT;
BEGIN
    -- ── Floors ────────────────────────────────────────────────────────────────

    IF NOT EXISTS (SELECT 1 FROM floor WHERE floor_number = 1) THEN
        INSERT INTO floor (floor_number, name) VALUES (1, '1층') RETURNING id INTO v_f1;
    ELSE
        SELECT id INTO v_f1 FROM floor WHERE floor_number = 1 LIMIT 1;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM floor WHERE floor_number = 2) THEN
        INSERT INTO floor (floor_number, name) VALUES (2, '2층') RETURNING id INTO v_f2;
    ELSE
        SELECT id INTO v_f2 FROM floor WHERE floor_number = 2 LIMIT 1;
    END IF;

    -- ── 1F rooms ──────────────────────────────────────────────────────────────
    -- Arc in 1-1 대예배실 approximated with one midpoint at (505,135) on the
    -- quarter-circle centered at (470,170) r=50.

    INSERT INTO room (floor_id, name, description, points)
    SELECT v_f1, t.rname, '', t.pts::jsonb
    FROM (VALUES
        ('1-1 대예배실',
         '[[0.022,0.240],[0.522,0.240],[0.561,0.269],[0.578,0.340],[0.578,0.940],[0.022,0.940]]'),
        ('1-2 소예배실',
         '[[0.022,0.040],[0.300,0.040],[0.300,0.200],[0.022,0.200]]'),
        ('1-4 E1',
         '[[0.311,0.040],[0.422,0.040],[0.422,0.200],[0.311,0.200]]'),
        ('1-5 새가족실',
         '[[0.433,0.040],[0.578,0.040],[0.578,0.200],[0.433,0.200]]'),
        ('3-2 식당',
         '[[0.611,0.040],[0.978,0.040],[0.978,0.348],[0.611,0.348]]'),
        ('3-1 카페테리아',
         '[[0.611,0.348],[0.867,0.348],[0.867,0.640],[0.611,0.640]]'),
        ('3-3 부엌',
         '[[0.867,0.348],[0.978,0.348],[0.978,0.640],[0.867,0.640]]'),
        ('1-3 유아유치부실',
         '[[0.611,0.660],[0.978,0.660],[0.978,0.940],[0.611,0.940]]'),
        ('계단',
         '[[0.583,0.080],[0.606,0.080],[0.606,0.200],[0.583,0.200]]')
    ) AS t(rname, pts)
    WHERE NOT EXISTS (
        SELECT 1 FROM room r WHERE r.floor_id = v_f1 AND r.name = t.rname
    );

    -- ── 2F rooms ──────────────────────────────────────────────────────────────
    -- 대예배실 2층: the upper-tier balcony polygon matching 1F main sanctuary footprint.
    -- Two unlabeled storage rects in the bottom-right corner are omitted.

    INSERT INTO room (floor_id, name, description, points)
    SELECT v_f2, t.rname, '', t.pts::jsonb
    FROM (VALUES
        ('대예배실 2층',
         '[[0.044,0.280],[0.356,0.280],[0.467,0.480],[0.467,0.940],[0.044,0.940]]'),
        ('2-4 O2 제자반교실',
         '[[0.022,0.040],[0.222,0.040],[0.222,0.200],[0.022,0.200]]'),
        ('2-3 O1',
         '[[0.222,0.040],[0.356,0.040],[0.356,0.200],[0.222,0.200]]'),
        ('2-2 성가대실',
         '[[0.356,0.040],[0.600,0.040],[0.600,0.200],[0.356,0.200]]'),
        ('계단',
         '[[0.622,0.040],[0.678,0.040],[0.678,0.240],[0.622,0.240]]'),
        ('2-1 비전홀',
         '[[0.722,0.040],[0.967,0.040],[0.967,0.380],[0.722,0.380]]'),
        ('4-1 행정실',
         '[[0.844,0.380],[0.967,0.380],[0.967,0.540],[0.844,0.540]]'),
        ('4-2 도서관&휴게',
         '[[0.522,0.620],[0.722,0.620],[0.722,0.860],[0.522,0.860]]')
    ) AS t(rname, pts)
    WHERE NOT EXISTS (
        SELECT 1 FROM room r WHERE r.floor_id = v_f2 AND r.name = t.rname
    );
END;
$$;
