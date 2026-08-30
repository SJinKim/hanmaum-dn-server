-- HDN-113: "이런 분께 권합니다" — who a course is meant for
--
-- An ordered list of short lines shown as its own section on the 양육 detail page.
-- Modelled exactly like ministry_requirements: an owned child table keyed by
-- (training_id, display_order), so the order the admin wrote them in survives.
--
-- ON DELETE CASCADE is safe here and only here: unlike user_training, these rows carry
-- no member data and have no meaning without their course.

CREATE TABLE training_target_audience
(
    training_id   BIGINT  NOT NULL REFERENCES training (id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL,
    description   TEXT    NOT NULL,
    PRIMARY KEY (training_id, display_order)
);
