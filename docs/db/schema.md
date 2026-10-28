<!-- Erzeugt von ./gradlew schemaSnapshot aus den Flyway-Migrationen. Nicht von Hand bearbeiten, siehe docs/db/README.md. -->
# DB-Schema

- Migrations-SHA-256: `9b62548f3d7022bbfbc49e65b9e88e00721ebeaed210f001004d8ce8f7f31555`
- Tabellen: 57 · Spalten: 589
- Fachliche Notizen: `docs/db/schema-notes.md`
- Spalten ohne `NOT NULL` sind nullable. Indizes, die nur einen Primärschlüssel oder Unique-Constraint tragen, stehen beim Constraint.

## public.albums

- `id` bigint NOT NULL DEFAULT `nextval('albums_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `name` character varying(200) NOT NULL
- `pcloud_code` character varying(100) NOT NULL
- `display_order` integer NOT NULL DEFAULT `0`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `albums_pcloud_code_key` `UNIQUE (pcloud_code)`
- `albums_pkey` `PRIMARY KEY (id)`
- `albums_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_albums_display_order` `btree (display_order) WHERE (deleted_at IS NULL)`

## public.announcements

- `id` bigint NOT NULL DEFAULT `nextval('announcements_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `title` character varying(255) NOT NULL
- `body` text NOT NULL
- `category` character varying(50) NOT NULL
- `start_at` timestamp with time zone NOT NULL
- `end_at` timestamp with time zone
- `is_pinned` boolean DEFAULT `false`
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `delete_entry_at` timestamp with time zone
- `image_url` character varying(2048)
- `location` character varying(255)
- `view_count` bigint NOT NULL DEFAULT `0`

Constraints:
- `announcements_pkey` `PRIMARY KEY (id)`
- `announcements_public_id_key` `UNIQUE (public_id)`

## public.attendance_definitions

- `id` bigint NOT NULL DEFAULT `nextval('attendance_definitions_id_seq'::regclass)`
- `day_of_week` character varying(20) NOT NULL
- `window_start` time without time zone NOT NULL
- `window_end` time without time zone NOT NULL
- `title` character varying(100) NOT NULL
- `is_active` boolean DEFAULT `true`
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `description` character varying(500)

Constraints:
- `attendance_definitions_pkey` `PRIMARY KEY (id)`
- `uq_attendance_definitions_public_id` `UNIQUE (public_id)`

Indizes:
- `idx_attendance_definitions_public_id` `btree (public_id)`

## public.attendance_logs

- `id` bigint NOT NULL DEFAULT `nextval('attendance_logs_id_seq'::regclass)`
- `member_id` bigint
- `attendance_date` date NOT NULL
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `definition_id` bigint NOT NULL
- `attended` boolean NOT NULL DEFAULT `true`
- `delete_entry_at` timestamp with time zone
- `group_id_at_check_in` bigint
- `presence` character varying(16) NOT NULL DEFAULT `'UNCONFIRMED'::character varying`

Constraints:
- `attendance_logs_definition_id_fkey` `FOREIGN KEY (definition_id) REFERENCES attendance_definitions(id) ON DELETE RESTRICT`
- `attendance_logs_group_id_at_check_in_fkey` `FOREIGN KEY (group_id_at_check_in) REFERENCES church_groups(id) ON DELETE RESTRICT`
- `attendance_logs_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL`
- `attendance_logs_pkey` `PRIMARY KEY (id)`
- `ck_attendance_log_presence` `CHECK (((presence)::text = ANY ((ARRAY['IN_PLACE'::character varying, 'OUTSIDE'::character varying, 'UNCONFIRMED'::character varying])::text[])))`
- `uq_attendance_log` `UNIQUE (member_id, definition_id, attendance_date)`
- `uq_attendance_logs_public_id` `UNIQUE (public_id)`

Indizes:
- `idx_attendance_date` `btree (attendance_date)`
- `idx_attendance_logs_definition_id` `btree (definition_id)`
- `idx_attendance_logs_group_counts` `btree (definition_id, attendance_date, group_id_at_check_in) WHERE ((deleted_at IS NULL) AND (attended = true))`
- `idx_attendance_logs_public_id` `btree (public_id)`

## public.bulletin_announcement

- `edition_id` bigint NOT NULL
- `position` integer NOT NULL
- `title` character varying(200) NOT NULL
- `body` character varying(2000)

Constraints:
- `bulletin_announcement_edition_id_fkey` `FOREIGN KEY (edition_id) REFERENCES bulletin_edition(id) ON DELETE CASCADE`
- `bulletin_announcement_pkey` `PRIMARY KEY (edition_id, "position")`

## public.bulletin_edition

- `id` bigint NOT NULL DEFAULT `nextval('bulletin_edition_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `service_date` date NOT NULL
- `status` character varying(20) NOT NULL DEFAULT `'DRAFT'::character varying`
- `volume` integer
- `service_id` bigint NOT NULL
- `service_name` character varying(50)
- `service_start_time` time without time zone
- `opening_prayer_by` character varying(100)
- `offering_song_by` character varying(100)
- `scripture_reference` character varying(100)
- `sermon_title` character varying(200)
- `sermon_preacher` character varying(100)
- `response_prayer_by` character varying(100)
- `response_song` character varying(200)
- `published_at` timestamp with time zone
- `withdrawn_at` timestamp with time zone
- `version` bigint NOT NULL DEFAULT `0`
- `created_by` character varying(64) NOT NULL
- `updated_by` character varying(64)
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `bulletin_edition_pkey` `PRIMARY KEY (id)`
- `bulletin_edition_public_id_key` `UNIQUE (public_id)`
- `ck_bulletin_edition_published` `CHECK ((((status)::text <> 'PUBLISHED'::text) OR (published_at IS NOT NULL)))`
- `ck_bulletin_edition_status` `CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'WITHDRAWN'::character varying])::text[])))`
- `ck_bulletin_edition_sunday` `CHECK ((EXTRACT(isodow FROM service_date) = (7)::numeric))`
- `ck_bulletin_edition_volume` `CHECK (((volume IS NULL) OR (volume > 0)))`
- `fk_bulletin_edition_service` `FOREIGN KEY (service_id) REFERENCES bulletin_service(id)`
- `uq_bulletin_edition_volume` `UNIQUE (volume)`

Indizes:
- `idx_bulletin_edition_service` `btree (service_id)`
- `uq_bulletin_edition_service_date` UNIQUE `btree (service_date) WHERE (deleted_at IS NULL)`

## public.bulletin_section_title

- `key` character varying(40) NOT NULL
- `title` character varying(50) NOT NULL
- `default_title` character varying(50) NOT NULL
- `updated_by` character varying(64)
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone

Constraints:
- `bulletin_section_title_pkey` `PRIMARY KEY (key)`
- `ck_bulletin_section_title_key` `CHECK (((key)::text = ANY ((ARRAY['SECTION_WORSHIP'::character varying, 'SECTION_OFFERING'::character varying, 'SECTION_SENDING'::character varying, 'FIXED_BLESSING_PRAYER'::character varying])::text[])))`

## public.bulletin_service

- `id` bigint NOT NULL DEFAULT `nextval('bulletin_service_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `name` character varying(50) NOT NULL
- `start_time` time without time zone NOT NULL
- `sort_order` integer NOT NULL
- `active` boolean NOT NULL DEFAULT `true`
- `is_bulletin_default` boolean NOT NULL DEFAULT `false`
- `created_by` character varying(64)
- `updated_by` character varying(64)
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `bulletin_service_pkey` `PRIMARY KEY (id)`
- `bulletin_service_public_id_key` `UNIQUE (public_id)`

Indizes:
- `uq_bulletin_service_default` UNIQUE `btree (is_bulletin_default) WHERE is_bulletin_default`

## public.bulletin_sharing_block

- `edition_id` bigint NOT NULL
- `position` integer NOT NULL
- `type` character varying(20) NOT NULL
- `text` character varying(2000) NOT NULL
- `reference` character varying(100)

Constraints:
- `bulletin_sharing_block_edition_id_fkey` `FOREIGN KEY (edition_id) REFERENCES bulletin_edition(id) ON DELETE CASCADE`
- `bulletin_sharing_block_pkey` `PRIMARY KEY (edition_id, "position")`
- `ck_bulletin_sharing_block_reference` `CHECK ((((type)::text = 'SCRIPTURE'::text) OR (reference IS NULL)))`
- `ck_bulletin_sharing_block_type` `CHECK (((type)::text = ANY ((ARRAY['HEADING'::character varying, 'PARAGRAPH'::character varying, 'SCRIPTURE'::character varying, 'QUESTION'::character varying])::text[])))`

## public.bulletin_song

- `edition_id` bigint NOT NULL
- `position` integer NOT NULL
- `title` character varying(200) NOT NULL

Constraints:
- `bulletin_song_edition_id_fkey` `FOREIGN KEY (edition_id) REFERENCES bulletin_edition(id) ON DELETE CASCADE`
- `bulletin_song_pkey` `PRIMARY KEY (edition_id, "position")`

## public.car_passengers

- `id` bigint NOT NULL DEFAULT `nextval('car_passengers_id_seq'::regclass)`
- `car_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`

Constraints:
- `car_passengers_car_id_fkey` `FOREIGN KEY (car_id) REFERENCES cars(id) ON DELETE CASCADE`
- `car_passengers_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `car_passengers_pkey` `PRIMARY KEY (id)`
- `uq_car_passengers_public_id` `UNIQUE (public_id)`
- `uq_member_car_assignment` `UNIQUE (member_id, car_id)`

Indizes:
- `idx_car_passengers_public_id` `btree (public_id)`

## public.cars

- `id` bigint NOT NULL DEFAULT `nextval('cars_id_seq'::regclass)`
- `driver_member_id` bigint NOT NULL
- `session_date` date NOT NULL
- `name` character varying(100)
- `max_seats` integer NOT NULL
- `current_passengers` integer DEFAULT `0`
- `departure_location` character varying(255)
- `departure_time` time without time zone
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`

Constraints:
- `cars_driver_member_id_fkey` `FOREIGN KEY (driver_member_id) REFERENCES members(id)`
- `cars_pkey` `PRIMARY KEY (id)`
- `uq_cars_public_id` `UNIQUE (public_id)`

Indizes:
- `idx_cars_public_id` `btree (public_id)`

## public.church_groups

- `id` bigint NOT NULL DEFAULT `nextval('church_groups_id_seq'::regclass)`
- `division` character varying(50)
- `name` character varying(100) NOT NULL
- `created_at` timestamp with time zone DEFAULT `now()`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `is_newcomer_group` boolean NOT NULL DEFAULT `false`

Constraints:
- `church_groups_pkey` `PRIMARY KEY (id)`
- `uq_church_groups_public_id` `UNIQUE (public_id)`
- `uq_group_division_name` `UNIQUE (division, name)`

Indizes:
- `idx_church_groups_public_id` `btree (public_id)`
- `idx_groups_division` `btree (division)`
- `uq_church_groups_newcomer_group` UNIQUE `btree (is_newcomer_group) WHERE (is_newcomer_group AND (deleted_at IS NULL))`

## public.course_application_attempts

- `id` bigint NOT NULL DEFAULT `nextval('course_application_attempts_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `member_id` bigint NOT NULL
- `training_id` bigint NOT NULL
- `external_course_id` integer NOT NULL
- `external_course_name` character varying(255) NOT NULL
- `client_application_id` uuid NOT NULL
- `external_application_id` bigint
- `status` character varying(16) NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `CURRENT_TIMESTAMP`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_course_application_attempt_created_has_external_id` `CHECK ((((status)::text <> 'CREATED'::text) OR (external_application_id IS NOT NULL)))`
- `ck_course_application_attempt_status` `CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CREATED'::character varying, 'CANCELLED'::character varying])::text[])))`
- `course_application_attempts_client_application_id_key` `UNIQUE (client_application_id)`
- `course_application_attempts_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `course_application_attempts_pkey` `PRIMARY KEY (id)`
- `course_application_attempts_public_id_key` `UNIQUE (public_id)`
- `course_application_attempts_training_id_fkey` `FOREIGN KEY (training_id) REFERENCES training(id)`

Indizes:
- `idx_course_application_attempt_member` `btree (member_id) WHERE (deleted_at IS NULL)`
- `uq_course_application_attempt_member_course` UNIQUE `btree (member_id, external_course_id) WHERE ((deleted_at IS NULL) AND ((status)::text <> 'CANCELLED'::text))`

## public.device_tokens

- `id` bigint NOT NULL DEFAULT `nextval('device_tokens_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `member_id` bigint NOT NULL
- `token` character varying(512) NOT NULL
- `platform` character varying(16) NOT NULL
- `created_at` timestamp with time zone NOT NULL
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `device_tokens_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `device_tokens_pkey` `PRIMARY KEY (id)`
- `device_tokens_public_id_key` `UNIQUE (public_id)`
- `device_tokens_token_key` `UNIQUE (token)`

Indizes:
- `idx_device_tokens_member` `btree (member_id)`

## public.dishwashing_schedule

- `id` bigint NOT NULL DEFAULT `nextval('dishwashing_schedule_id_seq'::regclass)`
- `scheduled_date` date NOT NULL
- `group_id` bigint NOT NULL
- `note` character varying(255)
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`

Constraints:
- `dishwashing_schedule_group_id_fkey` `FOREIGN KEY (group_id) REFERENCES church_groups(id)`
- `dishwashing_schedule_pkey` `PRIMARY KEY (id)`
- `uq_dishwashing_date_group` `UNIQUE (scheduled_date, group_id)`
- `uq_dishwashing_schedule_public_id` `UNIQUE (public_id)`

Indizes:
- `idx_dishwashing_schedule_public_id` `btree (public_id)`

## public.event_rsvp_logs

- `id` bigint NOT NULL DEFAULT `nextval('event_rsvp_logs_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `event_rsvp_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `group_id_at_rsvp` bigint
- `checked_in_at` timestamp with time zone NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `CURRENT_TIMESTAMP`
- `deleted_at` timestamp with time zone
- `updated_at` timestamp with time zone
- `status` character varying(16) NOT NULL DEFAULT `'GOING'::character varying`
- `reminder_count` integer NOT NULL DEFAULT `0`
- `last_reminded_at` timestamp with time zone

Constraints:
- `ck_event_rsvp_log_status` `CHECK (((status)::text = ANY ((ARRAY['GOING'::character varying, 'NOT_GOING'::character varying, 'MAYBE'::character varying])::text[])))`
- `event_rsvp_logs_event_rsvp_id_fkey` `FOREIGN KEY (event_rsvp_id) REFERENCES event_rsvps(id) ON DELETE RESTRICT`
- `event_rsvp_logs_group_id_at_rsvp_fkey` `FOREIGN KEY (group_id_at_rsvp) REFERENCES church_groups(id) ON DELETE RESTRICT`
- `event_rsvp_logs_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE RESTRICT`
- `event_rsvp_logs_pkey` `PRIMARY KEY (id)`
- `event_rsvp_logs_public_id_key` `UNIQUE (public_id)`
- `uq_event_rsvp_log` `UNIQUE (event_rsvp_id, member_id)`

Indizes:
- `idx_event_rsvp_logs_attendees` `btree (event_rsvp_id, checked_in_at)`
- `idx_event_rsvp_logs_reminder` `btree (status, reminder_count) WHERE (deleted_at IS NULL)`
- `idx_event_rsvp_logs_rsvp_member` `btree (event_rsvp_id, member_id)`

## public.event_rsvps

- `id` bigint NOT NULL DEFAULT `nextval('event_rsvps_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `announcement_id` bigint
- `title` character varying(100) NOT NULL
- `window_start` timestamp with time zone NOT NULL
- `window_end` timestamp with time zone NOT NULL
- `is_active` boolean NOT NULL DEFAULT `true`
- `created_at` timestamp with time zone NOT NULL DEFAULT `CURRENT_TIMESTAMP`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `description` character varying(500)

Constraints:
- `event_rsvps_announcement_id_fkey` `FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE SET NULL`
- `event_rsvps_pkey` `PRIMARY KEY (id)`
- `event_rsvps_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_event_rsvps_window` `btree (window_start, window_end, is_active) WHERE (deleted_at IS NULL)`

## public.family_relationships

- `id` bigint NOT NULL DEFAULT `nextval('family_relationships_id_seq'::regclass)`
- `member_id` bigint NOT NULL
- `related_member_id` bigint NOT NULL
- `relationship_type` character varying(50) NOT NULL
- `created_at` timestamp with time zone DEFAULT `now()`

Constraints:
- `family_relationships_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `family_relationships_pkey` `PRIMARY KEY (id)`
- `family_relationships_related_member_id_fkey` `FOREIGN KEY (related_member_id) REFERENCES members(id)`
- `uq_family_rel` `UNIQUE (member_id, related_member_id)`

## public.floor

- `id` bigint NOT NULL DEFAULT `nextval('floor_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `floor_number` integer NOT NULL
- `name` character varying(100) NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `floor_pkey` `PRIMARY KEY (id)`
- `floor_public_id_key` `UNIQUE (public_id)`

## public.flyway_schema_history (technisch: Flyway-Verlauf)

- `installed_rank` integer NOT NULL
- `version` character varying(50)
- `description` character varying(200) NOT NULL
- `type` character varying(20) NOT NULL
- `script` character varying(1000) NOT NULL
- `checksum` integer
- `installed_by` character varying(100) NOT NULL
- `installed_on` timestamp without time zone NOT NULL DEFAULT `now()`
- `execution_time` integer NOT NULL
- `success` boolean NOT NULL

Constraints:
- `flyway_schema_history_pk` `PRIMARY KEY (installed_rank)`

Indizes:
- `flyway_schema_history_s_idx` `btree (success)`

## public.group_leaders

- `id` bigint NOT NULL DEFAULT `nextval('group_leaders_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `group_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `start_date` date NOT NULL
- `end_date` date
- `created_at` timestamp with time zone NOT NULL
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `group_leaders_group_id_fkey` `FOREIGN KEY (group_id) REFERENCES church_groups(id)`
- `group_leaders_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `group_leaders_pkey` `PRIMARY KEY (id)`
- `group_leaders_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_group_leaders_member` `btree (member_id)`
- `uq_group_leaders_active_per_group` UNIQUE `btree (group_id) WHERE ((end_date IS NULL) AND (deleted_at IS NULL))`

## public.group_meetings

- `id` bigint NOT NULL DEFAULT `nextval('group_meetings_id_seq'::regclass)`
- `group_id` bigint NOT NULL
- `meeting_time` timestamp with time zone NOT NULL
- `location` character varying(255)
- `description` text
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`

Constraints:
- `group_meetings_group_id_fkey` `FOREIGN KEY (group_id) REFERENCES church_groups(id)`
- `group_meetings_pkey` `PRIMARY KEY (id)`
- `uq_group_meetings_public_id` `UNIQUE (public_id)`

Indizes:
- `idx_group_meetings_public_id` `btree (public_id)`

## public.guide_counting_areas

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `room_id` bigint NOT NULL
- `label` character varying(200) NOT NULL
- `display_order` integer NOT NULL
- `active` boolean NOT NULL DEFAULT `true`
- `version` bigint NOT NULL DEFAULT `0`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `guide_counting_areas_display_order_check` `CHECK ((display_order >= 0))`
- `guide_counting_areas_label_check` `CHECK ((length(btrim((label)::text)) > 0))`
- `guide_counting_areas_pkey` `PRIMARY KEY (id)`
- `guide_counting_areas_public_id_key` `UNIQUE (public_id)`
- `guide_counting_areas_room_id_fkey` `FOREIGN KEY (room_id) REFERENCES room(id)`

Indizes:
- `guide_counting_areas_room` `btree (room_id)`

## public.guide_import_batches

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `public_id` uuid NOT NULL
- `source_sha256` character varying(64) NOT NULL
- `mapping_version` character varying(40) NOT NULL
- `rolled_back_at` timestamp with time zone
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `guide_import_batches_pkey` `PRIMARY KEY (id)`
- `guide_import_batches_public_id_key` `UNIQUE (public_id)`

## public.guide_import_records

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `batch_id` bigint NOT NULL
- `report_id` bigint NOT NULL
- `service_date` date NOT NULL
- `source_row` integer NOT NULL
- `source_data` text NOT NULL
- `canonical_sha256` character varying(64) NOT NULL
- `imported_version` bigint NOT NULL

Constraints:
- `guide_import_records_batch_id_fkey` `FOREIGN KEY (batch_id) REFERENCES guide_import_batches(id)`
- `guide_import_records_pkey` `PRIMARY KEY (id)`
- `guide_import_records_report_id_fkey` `FOREIGN KEY (report_id) REFERENCES guide_worship_reports(id)`
- `guide_import_records_report_id_key` `UNIQUE (report_id)`
- `guide_import_records_service_date_key` `UNIQUE (service_date)`

## public.guide_report_areas

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `report_id` bigint NOT NULL
- `area_id` bigint NOT NULL
- `room_id` bigint NOT NULL
- `room_name` character varying(200) NOT NULL
- `label` character varying(200) NOT NULL
- `display_order` integer NOT NULL

Constraints:
- `guide_report_area_owner` `UNIQUE (id, report_id)`
- `guide_report_area_unique` `UNIQUE (report_id, area_id)`
- `guide_report_areas_area_id_fkey` `FOREIGN KEY (area_id) REFERENCES guide_counting_areas(id)`
- `guide_report_areas_pkey` `PRIMARY KEY (id)`
- `guide_report_areas_report_id_fkey` `FOREIGN KEY (report_id) REFERENCES guide_worship_reports(id) ON DELETE CASCADE`
- `guide_report_areas_room_id_fkey` `FOREIGN KEY (room_id) REFERENCES room(id)`

## public.guide_summary_conversions

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `report_id` bigint NOT NULL
- `source_version` bigint NOT NULL
- `source_date` date NOT NULL
- `source_service1_count` integer
- `source_service2_count` integer
- `source_service3_count` integer
- `source_notes` text
- `converted_by` character varying(255) NOT NULL
- `converted_at` timestamp with time zone NOT NULL

Constraints:
- `guide_summary_conversions_pkey` `PRIMARY KEY (id)`
- `guide_summary_conversions_report_id_fkey` `FOREIGN KEY (report_id) REFERENCES guide_worship_reports(id) ON DELETE CASCADE`
- `guide_summary_conversions_report_id_key` `UNIQUE (report_id)`

## public.guide_worship_details

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `report_id` bigint NOT NULL
- `service_number` integer NOT NULL
- `adult_count` integer
- `school_count` integer
- `youth_count` integer
- `report_area_id` bigint NOT NULL

Constraints:
- `guide_detail_area_owner` `FOREIGN KEY (report_area_id, report_id) REFERENCES guide_report_areas(id, report_id) ON DELETE CASCADE`
- `guide_detail_area_service` `UNIQUE (report_area_id, service_number)`
- `guide_detail_youth_service` `CHECK (((service_number = 3) OR (youth_count IS NULL)))`
- `guide_worship_details_adult_count_check` `CHECK ((adult_count >= 0))`
- `guide_worship_details_pkey` `PRIMARY KEY (id)`
- `guide_worship_details_report_id_fkey` `FOREIGN KEY (report_id) REFERENCES guide_worship_reports(id) ON DELETE CASCADE`
- `guide_worship_details_school_count_check` `CHECK ((school_count >= 0))`
- `guide_worship_details_service_number_check` `CHECK (((service_number >= 1) AND (service_number <= 3)))`
- `guide_worship_details_youth_count_check` `CHECK ((youth_count >= 0))`

## public.guide_worship_reports

- `id` bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY
- `public_id` uuid NOT NULL
- `service_date` date NOT NULL
- `service1_count` integer
- `service2_count` integer
- `service3_count` integer
- `notes` text
- `version` bigint NOT NULL DEFAULT `0`
- `created_by` character varying(255) NOT NULL
- `updated_by` character varying(255) NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `report_mode` character varying(16) NOT NULL DEFAULT `'SUMMARY_ONLY'::character varying`
- `detail_revision` bigint NOT NULL DEFAULT `0`

Constraints:
- `guide_report_mode_valid` `CHECK (((report_mode)::text = ANY ((ARRAY['SUMMARY_ONLY'::character varying, 'DETAILED'::character varying])::text[])))`
- `guide_worship_reports_pkey` `PRIMARY KEY (id)`
- `guide_worship_reports_public_id_key` `UNIQUE (public_id)`
- `guide_worship_reports_service1_count_check` `CHECK ((service1_count >= 0))`
- `guide_worship_reports_service2_count_check` `CHECK ((service2_count >= 0))`
- `guide_worship_reports_service3_count_check` `CHECK ((service3_count >= 0))`

Indizes:
- `guide_worship_reports_active_date` UNIQUE `btree (service_date) WHERE (deleted_at IS NULL)`

## public.meeting_attendances

- `id` bigint NOT NULL DEFAULT `nextval('meeting_attendances_id_seq'::regclass)`
- `meeting_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `attendance_status` character varying(20) NOT NULL DEFAULT `'PRESENT'::character varying`
- `prayer_request` text
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`

Constraints:
- `meeting_attendances_meeting_id_fkey` `FOREIGN KEY (meeting_id) REFERENCES group_meetings(id)`
- `meeting_attendances_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `meeting_attendances_pkey` `PRIMARY KEY (id)`
- `uq_meeting_attendances_public_id` `UNIQUE (public_id)`
- `uq_meeting_member` `UNIQUE (meeting_id, member_id)`

Indizes:
- `idx_meeting_attendances_public_id` `btree (public_id)`

## public.member_claim_conflicts

- `id` bigint NOT NULL DEFAULT `nextval('member_claim_conflicts_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `registration_member_id` bigint NOT NULL
- `candidate_member_id` bigint NOT NULL
- `reason` character varying(64) NOT NULL
- `conflict_fields` character varying(500)
- `created_at` timestamp with time zone NOT NULL DEFAULT `CURRENT_TIMESTAMP`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `member_claim_conflicts_candidate_member_id_fkey` `FOREIGN KEY (candidate_member_id) REFERENCES members(id)`
- `member_claim_conflicts_pkey` `PRIMARY KEY (id)`
- `member_claim_conflicts_public_id_key` `UNIQUE (public_id)`
- `member_claim_conflicts_registration_member_id_fkey` `FOREIGN KEY (registration_member_id) REFERENCES members(id)`

Indizes:
- `uq_member_claim_conflicts_open_registration` UNIQUE `btree (registration_member_id) WHERE (deleted_at IS NULL)`

## public.member_graduations

- `id` bigint NOT NULL DEFAULT `nextval('member_graduations_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `member_id` bigint NOT NULL
- `graduated_on` date NOT NULL
- `reason` character varying(30) NOT NULL
- `note` text
- `graduated_by` character varying(64) NOT NULL
- `previous_member_status` character varying(20) NOT NULL
- `reverted_at` timestamp with time zone
- `reverted_by` character varying(64)
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_member_graduations_reason` `CHECK (((reason)::text = ANY ((ARRAY['MARRIAGE'::character varying, 'RELOCATION'::character varying, 'AGE_OUT'::character varying, 'OTHER'::character varying])::text[])))`
- `ck_member_graduations_revert` `CHECK (((reverted_at IS NULL) = (reverted_by IS NULL)))`
- `fk_member_graduations_member` `FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE`
- `member_graduations_pkey` `PRIMARY KEY (id)`
- `member_graduations_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_member_graduations_member` `btree (member_id)`
- `uq_member_graduations_open` UNIQUE `btree (member_id) WHERE ((reverted_at IS NULL) AND (deleted_at IS NULL))`

## public.member_reconciliation_candidates

- `reconciliation_id` bigint NOT NULL
- `member_id` bigint NOT NULL

Constraints:
- `fk_reconciliation_candidates_case` `FOREIGN KEY (reconciliation_id) REFERENCES member_reconciliations(id) ON DELETE CASCADE`
- `fk_reconciliation_candidates_member` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `member_reconciliation_candidates_pkey` `PRIMARY KEY (reconciliation_id, member_id)`

## public.member_reconciliations

- `id` bigint NOT NULL DEFAULT `nextval('member_reconciliations_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `registration_member_id` bigint NOT NULL
- `status` character varying(20) NOT NULL DEFAULT `'OPEN'::character varying`
- `reasons` character varying(500) NOT NULL
- `conflict_fields` character varying(500)
- `selected_member_id` bigint
- `resolved_by` character varying(64)
- `resolved_at` timestamp with time zone
- `version` bigint NOT NULL DEFAULT `0`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_member_reconciliations_status` `CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'LINKED'::character varying, 'DISMISSED'::character varying])::text[])))`
- `fk_member_reconciliations_registration` `FOREIGN KEY (registration_member_id) REFERENCES members(id)`
- `fk_member_reconciliations_selected` `FOREIGN KEY (selected_member_id) REFERENCES members(id)`
- `member_reconciliations_pkey` `PRIMARY KEY (id)`
- `member_reconciliations_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_member_reconciliations_status` `btree (status) WHERE (deleted_at IS NULL)`
- `uq_member_reconciliations_open_registration` UNIQUE `btree (registration_member_id) WHERE (((status)::text = 'OPEN'::text) AND (deleted_at IS NULL))`

## public.members

`keycloak_id` ist der `sub`-Claim des Keycloak-JWT und verknüpft das Login mit dem Mitglied
(Anlage beim ersten Login).

- `id` bigint NOT NULL DEFAULT `nextval('members_id_seq'::regclass)`
- `last_name` text NOT NULL
- `first_name` text NOT NULL
- `discriminator` text
- `gender` text
- `birth_date` text
- `phone_number` text
- `email` text
- `street` text
- `zip_code` text
- `city` text
- `registration_date` text
- `member_status` character varying(50) DEFAULT `'PENDING'::character varying`
- `group_id` bigint
- `created_at` timestamp with time zone DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `baptism` text
- `keycloak_id` text
- `profile_image_url` text
- `house_number` text
- `is_next_group_leader` boolean NOT NULL DEFAULT `false`
- `one_on_one_signup_filled` boolean NOT NULL DEFAULT `false`
- `email_lookup_hash` character varying(64)
- `keycloak_lookup_hash` character varying(64)
- `pii_key_id` character varying(50)
- `delete_entry_at` timestamp with time zone
- `push_enabled` boolean NOT NULL DEFAULT `true`
- `occupation` text
- `status_before_delete` character varying(50)
- `origin` character varying(20) NOT NULL DEFAULT `'MANUAL'::character varying`
- `identity_status` character varying(30)
- `work_or_school` text
- `profile_image_key` character varying(255)
- `profile_image_version` bigint NOT NULL DEFAULT `0`

Constraints:
- `ck_members_identity_status` `CHECK (((identity_status IS NULL) OR ((identity_status)::text = ANY ((ARRAY['EMPLOYEE'::character varying, 'UNIVERSITY_STUDENT'::character varying, 'EXAM_PREPARATION'::character varying, 'WORKING_HOLIDAY'::character varying, 'EXCHANGE_STUDENT'::character varying, 'SELF_EMPLOYED'::character varying, 'EXPATRIATE'::character varying, 'JOB_SEEKING'::character varying])::text[]))))`
- `ck_members_origin` `CHECK (((origin)::text = ANY ((ARRAY['MANUAL'::character varying, 'NEWCOMER_FORM'::character varying, 'APP'::character varying])::text[])))`
- `members_group_id_fkey` `FOREIGN KEY (group_id) REFERENCES church_groups(id)`
- `members_pkey` `PRIMARY KEY (id)`
- `uq_members_public_id` `UNIQUE (public_id)`

Indizes:
- `idx_members_city` `btree (city)`
- `idx_members_delete_entry_at` `btree (delete_entry_at) WHERE (deleted_at IS NOT NULL)`
- `idx_members_group` `btree (group_id)`
- `idx_members_public_id` `btree (public_id)`
- `uq_members_email_lookup_hash` UNIQUE `btree (email_lookup_hash) WHERE ((email_lookup_hash IS NOT NULL) AND (deleted_at IS NULL))`
- `uq_members_keycloak_lookup_hash` UNIQUE `btree (keycloak_lookup_hash) WHERE ((keycloak_lookup_hash IS NOT NULL) AND (deleted_at IS NULL))`

## public.ministries

- `id` bigint NOT NULL DEFAULT `nextval('ministries_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `name` character varying(255) NOT NULL
- `short_description` character varying(500) NOT NULL
- `image_url` character varying(255)
- `is_ministry_active` boolean NOT NULL DEFAULT `true`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `long_description` text NOT NULL

Constraints:
- `ministries_pkey` `PRIMARY KEY (id)`
- `ministries_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_ministries_public_id` `btree (public_id)`

## public.ministry_contacts

- `ministry_id` bigint NOT NULL
- `display_order` integer NOT NULL
- `role` character varying(50) NOT NULL
- `name` character varying(150) NOT NULL

Constraints:
- `ministry_contacts_ministry_id_fkey` `FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE CASCADE`
- `ministry_contacts_pkey` `PRIMARY KEY (ministry_id, display_order)`

## public.ministry_registrations

- `id` bigint NOT NULL DEFAULT `nextval('ministry_registrations_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `ministry_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `note` text
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `delete_entry_at` timestamp with time zone
- `start_date` date NOT NULL
- `end_date` date
- `assignment_role` character varying(20) NOT NULL DEFAULT `'MEMBER'::character varying`
- `assignment_status` character varying(20) NOT NULL DEFAULT `'ACTIVE'::character varying`
- `self_introduction` text
- `rejection_message` text
- `leader_notified_at` timestamp without time zone

Constraints:
- `ck_ministry_assignment_role` `CHECK (((assignment_role)::text = ANY ((ARRAY['LEADER'::character varying, 'SUB_LEADER'::character varying, 'MEMBER'::character varying])::text[])))`
- `ck_ministry_assignment_status` `CHECK (((assignment_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'PENDING'::character varying, 'REJECTED'::character varying])::text[])))`
- `fk_ministry_reg_member` `FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE`
- `ministry_registrations_ministry_id_fkey` `FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE CASCADE`
- `ministry_registrations_pkey` `PRIMARY KEY (id)`
- `ministry_registrations_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_ministry_registrations_member_id` `btree (member_id)`
- `idx_ministry_registrations_ministry_id` `btree (ministry_id)`
- `idx_ministry_registrations_public_id` `btree (public_id)`
- `uq_ministry_current_leader` UNIQUE `btree (ministry_id) WHERE (((assignment_role)::text = 'LEADER'::text) AND (end_date IS NULL) AND (deleted_at IS NULL))`
- `uq_ministry_pending_self_registration` UNIQUE `btree (ministry_id, member_id) WHERE (((assignment_status)::text = 'PENDING'::text) AND (end_date IS NULL) AND (deleted_at IS NULL))`

## public.ministry_requirements

- `ministry_id` bigint NOT NULL
- `display_order` integer NOT NULL
- `description` text NOT NULL

Constraints:
- `ministry_requirements_ministry_id_fkey` `FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE CASCADE`
- `ministry_requirements_pkey` `PRIMARY KEY (ministry_id, display_order)`

## public.ministry_schedules

- `ministry_id` bigint NOT NULL
- `display_order` integer NOT NULL
- `description` character varying(200) NOT NULL
- `start_time` time without time zone NOT NULL
- `end_time` time without time zone NOT NULL
- `location` character varying(100)
- `day_of_week` character varying(20)

Constraints:
- `ck_ministry_schedules_day_of_week` `CHECK (((day_of_week IS NULL) OR ((day_of_week)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[]))))`
- `ministry_schedules_ministry_id_fkey` `FOREIGN KEY (ministry_id) REFERENCES ministries(id) ON DELETE CASCADE`
- `ministry_schedules_pkey` `PRIMARY KEY (ministry_id, display_order)`

## public.newcomer_form_links

- `id` bigint NOT NULL DEFAULT `nextval('newcomer_form_links_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `token_hash` character varying(64) NOT NULL
- `expires_at` timestamp with time zone NOT NULL
- `revoked_at` timestamp with time zone
- `created_by` character varying(64) NOT NULL
- `use_count` bigint NOT NULL DEFAULT `0`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `newcomer_form_links_pkey` `PRIMARY KEY (id)`
- `newcomer_form_links_public_id_key` `UNIQUE (public_id)`
- `newcomer_form_links_token_hash_key` `UNIQUE (token_hash)`

Indizes:
- `idx_newcomer_form_links_active` `btree (expires_at) WHERE ((revoked_at IS NULL) AND (deleted_at IS NULL))`

## public.newcomer_form_submissions

- `id` bigint NOT NULL DEFAULT `nextval('newcomer_form_submissions_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `form_link_id` bigint NOT NULL
- `newcomer_profile_id` bigint NOT NULL
- `idempotency_hash` character varying(64) NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `fk_form_submissions_link` `FOREIGN KEY (form_link_id) REFERENCES newcomer_form_links(id)`
- `fk_form_submissions_profile` `FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id)`
- `newcomer_form_submissions_pkey` `PRIMARY KEY (id)`
- `newcomer_form_submissions_public_id_key` `UNIQUE (public_id)`
- `uq_form_submissions_idempotency` `UNIQUE (form_link_id, idempotency_hash)`

## public.newcomer_graduations

- `id` bigint NOT NULL DEFAULT `nextval('newcomer_graduations_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `newcomer_profile_id` bigint NOT NULL
- `member_id` bigint NOT NULL
- `group_id` bigint NOT NULL
- `cohort_number` integer NOT NULL
- `cohort_label` character varying(30) NOT NULL
- `graduated_on` date NOT NULL
- `assignment_reason` text
- `graduated_by` character varying(64) NOT NULL
- `version` bigint NOT NULL DEFAULT `0`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_newcomer_graduations_cohort` `CHECK (((cohort_number >= 1) AND (cohort_number <= 10)))`
- `fk_newcomer_graduations_group` `FOREIGN KEY (group_id) REFERENCES church_groups(id)`
- `fk_newcomer_graduations_member` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `fk_newcomer_graduations_profile` `FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id)`
- `newcomer_graduations_member_id_key` `UNIQUE (member_id)`
- `newcomer_graduations_newcomer_profile_id_key` `UNIQUE (newcomer_profile_id)`
- `newcomer_graduations_pkey` `PRIMARY KEY (id)`
- `newcomer_graduations_public_id_key` `UNIQUE (public_id)`

## public.newcomer_import_records

- `id` bigint NOT NULL DEFAULT `nextval('newcomer_import_records_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `source_fingerprint` character varying(64) NOT NULL
- `row_number` integer NOT NULL
- `payload_fingerprint` character varying(64) NOT NULL
- `member_id` bigint NOT NULL
- `newcomer_profile_id` bigint NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `fk_newcomer_import_records_member` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `fk_newcomer_import_records_profile` `FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id)`
- `newcomer_import_records_pkey` `PRIMARY KEY (id)`
- `newcomer_import_records_public_id_key` `UNIQUE (public_id)`
- `uq_newcomer_import_records_source_row` `UNIQUE (source_fingerprint, row_number)`

Indizes:
- `idx_newcomer_import_records_payload` `btree (payload_fingerprint) WHERE (deleted_at IS NULL)`

## public.newcomer_profiles

- `id` bigint NOT NULL DEFAULT `nextval('newcomer_profiles_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `member_id` bigint NOT NULL
- `lifecycle_status` character varying(20) NOT NULL DEFAULT `'SUBMITTED'::character varying`
- `intake_round` integer
- `has_visited` boolean NOT NULL DEFAULT `false`
- `caregiver_member_id` bigint
- `first_visit_date` text
- `assigned_group_id` bigint
- `assignment_reason` text
- `overall_notes` text
- `post_assignment_attendance` character varying(30)
- `kakao_id` text
- `previous_church` text
- `church_experience` character varying(30)
- `visit_motives` text
- `additional_notes` text
- `pii_key_id` character varying(50)
- `version` bigint NOT NULL DEFAULT `0`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `consent_version` character varying(50)
- `consented_at` timestamp with time zone

Constraints:
- `ck_newcomer_profiles_attendance` `CHECK (((post_assignment_attendance IS NULL) OR ((post_assignment_attendance)::text = ANY ((ARRAY['REGULAR'::character varying, 'OCCASIONAL'::character varying, 'WORSHIP_ONLY'::character varying, 'ABSENT_OVER_MONTH'::character varying, 'CHANGED_CHURCH'::character varying, 'RETURNED_OR_MOVED'::character varying])::text[]))))`
- `ck_newcomer_profiles_experience` `CHECK (((church_experience IS NULL) OR ((church_experience)::text = ANY ((ARRAY['FIRST_TIME'::character varying, 'CHILDHOOD_FEW_TIMES'::character varying, 'IRREGULAR'::character varying, 'REGULAR'::character varying])::text[]))))`
- `ck_newcomer_profiles_lifecycle` `CHECK (((lifecycle_status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'IN_CARE'::character varying, 'GRADUATED'::character varying, 'ARCHIVED'::character varying])::text[])))`
- `fk_newcomer_profiles_caregiver` `FOREIGN KEY (caregiver_member_id) REFERENCES members(id)`
- `fk_newcomer_profiles_group` `FOREIGN KEY (assigned_group_id) REFERENCES church_groups(id)`
- `fk_newcomer_profiles_member` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `newcomer_profiles_member_id_key` `UNIQUE (member_id)`
- `newcomer_profiles_pkey` `PRIMARY KEY (id)`
- `newcomer_profiles_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_newcomer_profiles_caregiver` `btree (caregiver_member_id) WHERE (deleted_at IS NULL)`
- `idx_newcomer_profiles_group` `btree (assigned_group_id) WHERE (deleted_at IS NULL)`
- `idx_newcomer_profiles_status` `btree (lifecycle_status) WHERE (deleted_at IS NULL)`

## public.newcomer_visits

- `id` bigint NOT NULL DEFAULT `nextval('newcomer_visits_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `visit_date` date NOT NULL
- `last_name` text NOT NULL
- `first_name` text NOT NULL
- `gender` text
- `birth_year` integer
- `visit_type` character varying(20) NOT NULL
- `source` character varying(30)
- `note` text
- `newcomer_profile_id` bigint
- `pii_key_id` character varying(50)
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `definition_id` bigint

Constraints:
- `ck_newcomer_visits_birth_year` `CHECK (((birth_year IS NULL) OR ((birth_year >= 1900) AND (birth_year <= 2100))))`
- `ck_newcomer_visits_source` `CHECK (((source IS NULL) OR ((source)::text = ANY ((ARRAY['FRIEND_FAMILY'::character varying, 'ADVERTISEMENT'::character varying, 'SOCIAL_MEDIA'::character varying, 'WEBSITE'::character varying, 'WALK_IN'::character varying, 'OTHER'::character varying])::text[]))))`
- `ck_newcomer_visits_type` `CHECK (((visit_type)::text = ANY ((ARRAY['FIRST'::character varying, 'REVISIT'::character varying])::text[])))`
- `fk_newcomer_visits_definition` `FOREIGN KEY (definition_id) REFERENCES attendance_definitions(id) ON DELETE SET NULL`
- `fk_newcomer_visits_profile` `FOREIGN KEY (newcomer_profile_id) REFERENCES newcomer_profiles(id) ON DELETE SET NULL`
- `newcomer_visits_pkey` `PRIMARY KEY (id)`
- `newcomer_visits_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_newcomer_visits_date` `btree (visit_date) WHERE (deleted_at IS NULL)`
- `idx_newcomer_visits_definition_date` `btree (definition_id, visit_date)`
- `idx_newcomer_visits_profile` `btree (newcomer_profile_id) WHERE (deleted_at IS NULL)`

## public.notifications

- `id` bigint NOT NULL DEFAULT `nextval('notifications_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `member_id` bigint NOT NULL
- `type` character varying(32) NOT NULL
- `title` character varying(255) NOT NULL
- `body` text NOT NULL
- `reference_type` character varying(32)
- `reference_public_id` uuid
- `seen_at` timestamp with time zone
- `read_at` timestamp with time zone
- `created_at` timestamp with time zone NOT NULL
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `notifications_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `notifications_pkey` `PRIMARY KEY (id)`
- `notifications_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_notifications_member_created` `btree (member_id, created_at DESC)`
- `idx_notifications_member_unseen` `btree (member_id) WHERE (seen_at IS NULL)`

## public.profile_image_cleanup

- `id` bigint NOT NULL DEFAULT `nextval('profile_image_cleanup_id_seq'::regclass)`
- `object_key` character varying(255) NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`

Constraints:
- `profile_image_cleanup_object_key_key` `UNIQUE (object_key)`
- `profile_image_cleanup_pkey` `PRIMARY KEY (id)`

## public.room

- `id` bigint NOT NULL DEFAULT `nextval('room_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `floor_id` bigint NOT NULL
- `name` character varying(200) NOT NULL
- `description` text NOT NULL DEFAULT `''::text`
- `points` jsonb NOT NULL DEFAULT `'[]'::jsonb`
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `room_floor_id_fkey` `FOREIGN KEY (floor_id) REFERENCES floor(id)`
- `room_pkey` `PRIMARY KEY (id)`
- `room_public_id_key` `UNIQUE (public_id)`

Indizes:
- `idx_room_floor_id` `btree (floor_id)`

## public.training

- `id` bigint NOT NULL DEFAULT `nextval('training_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `name` character varying(100) NOT NULL
- `sort_order` integer NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `code` character varying(50) NOT NULL
- `name_ko` character varying(100)
- `category` character varying(20)
- `has_cohorts` boolean NOT NULL DEFAULT `false`
- `prerequisite_training_id` bigint
- `is_active` boolean NOT NULL DEFAULT `true`
- `description` text
- `start_date` date
- `duration_weeks` integer
- `open_for_registration` boolean NOT NULL DEFAULT `false`
- `weekday` character varying(20)
- `start_time` time without time zone
- `duration_minutes` integer
- `location` character varying(200)
- `leader_name` character varying(150)
- `capacity` integer
- `registration_deadline` date

Constraints:
- `ck_training_capacity` `CHECK (((capacity IS NULL) OR (capacity > 0)))`
- `ck_training_category` `CHECK (((category IS NULL) OR ((category)::text = ANY ((ARRAY['FOUNDATION'::character varying, 'DISCIPLESHIP'::character varying, 'BIBLE'::character varying, 'MINISTRY'::character varying, 'MISSION'::character varying])::text[]))))`
- `ck_training_duration_minutes` `CHECK (((duration_minutes IS NULL) OR (duration_minutes > 0)))`
- `ck_training_duration_weeks` `CHECK (((duration_weeks IS NULL) OR (duration_weeks > 0)))`
- `ck_training_no_self_prerequisite` `CHECK (((prerequisite_training_id IS NULL) OR (prerequisite_training_id <> id)))`
- `ck_training_registration_deadline` `CHECK (((registration_deadline IS NULL) OR (start_date IS NULL) OR (registration_deadline <= start_date)))`
- `ck_training_weekday` `CHECK (((weekday IS NULL) OR ((weekday)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[]))))`
- `fk_training_prerequisite` `FOREIGN KEY (prerequisite_training_id) REFERENCES training(id) ON DELETE RESTRICT`
- `training_pkey` `PRIMARY KEY (id)`
- `training_public_id_key` `UNIQUE (public_id)`
- `uq_training_code` `UNIQUE (code)`
- `uq_training_name` `UNIQUE (name)`

Indizes:
- `idx_training_category` `btree (category)`
- `idx_training_open_for_registration` `btree (open_for_registration) WHERE open_for_registration`
- `idx_training_prerequisite` `btree (prerequisite_training_id) WHERE (prerequisite_training_id IS NOT NULL)`
- `idx_training_sort_order` `btree (sort_order)`

## public.training_alias

- `training_id` bigint NOT NULL
- `alias` character varying(100) NOT NULL

Constraints:
- `training_alias_pkey` `PRIMARY KEY (training_id, alias)`
- `training_alias_training_id_fkey` `FOREIGN KEY (training_id) REFERENCES training(id) ON DELETE CASCADE`

## public.training_cohort

- `id` bigint NOT NULL DEFAULT `nextval('training_cohort_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `training_id` bigint NOT NULL
- `series` character varying(20) NOT NULL DEFAULT `'POWER'::character varying`
- `ordinal` integer NOT NULL
- `label` character varying(100)
- `cohort_year` integer
- `term` character varying(20)
- `started_on` date
- `ended_on` date
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_training_cohort_ordinal` `CHECK ((ordinal > 0))`
- `ck_training_cohort_period` `CHECK (((started_on IS NULL) OR (ended_on IS NULL) OR (ended_on >= started_on)))`
- `ck_training_cohort_series` `CHECK (((series)::text = ANY ((ARRAY['LEGACY'::character varying, 'POWER'::character varying])::text[])))`
- `ck_training_cohort_term` `CHECK (((term IS NULL) OR ((term)::text = ANY ((ARRAY['FIRST_HALF'::character varying, 'SECOND_HALF'::character varying])::text[]))))`
- `ck_training_cohort_year` `CHECK (((cohort_year IS NULL) OR ((cohort_year >= 1970) AND (cohort_year <= 2100))))`
- `training_cohort_pkey` `PRIMARY KEY (id)`
- `training_cohort_public_id_key` `UNIQUE (public_id)`
- `training_cohort_training_id_fkey` `FOREIGN KEY (training_id) REFERENCES training(id) ON DELETE RESTRICT`

Indizes:
- `idx_training_cohort_training` `btree (training_id)`
- `uq_training_cohort_ordinal` UNIQUE `btree (training_id, series, ordinal) WHERE (deleted_at IS NULL)`

## public.training_target_audience

- `training_id` bigint NOT NULL
- `display_order` integer NOT NULL
- `description` text NOT NULL

Constraints:
- `training_target_audience_pkey` `PRIMARY KEY (training_id, display_order)`
- `training_target_audience_training_id_fkey` `FOREIGN KEY (training_id) REFERENCES training(id) ON DELETE CASCADE`

## public.user_training

- `id` bigint NOT NULL DEFAULT `nextval('user_training_id_seq'::regclass)`
- `public_id` uuid NOT NULL DEFAULT `gen_random_uuid()`
- `user_id` bigint NOT NULL
- `training_id` bigint NOT NULL
- `status` character varying(20) NOT NULL
- `completed_at` date
- `created_at` timestamp with time zone NOT NULL DEFAULT `now()`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone
- `cohort_id` bigint
- `applied_on` date
- `started_on` date
- `variant` character varying(30)
- `mentor_member_id` bigint
- `mentor_name_raw` text
- `note` text

Constraints:
- `ck_user_training_no_self_mentor` `CHECK (((mentor_member_id IS NULL) OR (mentor_member_id <> user_id)))`
- `ck_user_training_status` `CHECK (((status)::text = ANY ((ARRAY['APPLIED'::character varying, 'ENROLLED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'DROPPED'::character varying, 'UNKNOWN'::character varying])::text[])))`
- `ck_user_training_timeline` `CHECK ((((applied_on IS NULL) OR (started_on IS NULL) OR (started_on >= applied_on)) AND ((started_on IS NULL) OR (completed_at IS NULL) OR (completed_at >= started_on)) AND ((applied_on IS NULL) OR (completed_at IS NULL) OR (completed_at >= applied_on))))`
- `ck_user_training_variant` `CHECK (((variant IS NULL) OR ((variant)::text = ANY ((ARRAY['OLD_TESTAMENT'::character varying, 'NEW_TESTAMENT'::character varying])::text[]))))`
- `fk_user_training_cohort` `FOREIGN KEY (cohort_id) REFERENCES training_cohort(id) ON DELETE RESTRICT`
- `fk_user_training_mentor` `FOREIGN KEY (mentor_member_id) REFERENCES members(id) ON DELETE SET NULL`
- `user_training_pkey` `PRIMARY KEY (id)`
- `user_training_public_id_key` `UNIQUE (public_id)`
- `user_training_training_id_fkey` `FOREIGN KEY (training_id) REFERENCES training(id)`
- `user_training_user_id_fkey` `FOREIGN KEY (user_id) REFERENCES members(id) ON DELETE CASCADE`

Indizes:
- `idx_user_training_cohort` `btree (cohort_id) WHERE (cohort_id IS NOT NULL)`
- `idx_user_training_completed` `btree (completed_at) WHERE (completed_at IS NOT NULL)`
- `idx_user_training_mentor` `btree (mentor_member_id) WHERE (mentor_member_id IS NOT NULL)`
- `idx_user_training_training_id` `btree (training_id)`
- `idx_user_training_user_id` `btree (user_id)`
- `uq_user_training_member_training_variant` UNIQUE `btree (user_id, training_id, COALESCE(variant, ''::character varying)) WHERE (deleted_at IS NULL)`

## public.verse_records

- `id` bigint NOT NULL DEFAULT `nextval('verse_records_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `member_id` bigint NOT NULL
- `record_date` date NOT NULL
- `kind` character varying(16) NOT NULL
- `created_at` timestamp with time zone NOT NULL DEFAULT `CURRENT_TIMESTAMP`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_verse_record_kind` `CHECK (((kind)::text = ANY ((ARRAY['QUIET_TIME'::character varying, 'RECITATION'::character varying])::text[])))`
- `uq_verse_record` `UNIQUE (member_id, record_date, kind)`
- `verse_records_member_id_fkey` `FOREIGN KEY (member_id) REFERENCES members(id)`
- `verse_records_pkey` `PRIMARY KEY (id)`
- `verse_records_public_id_key` `UNIQUE (public_id)`

## public.weekly_verses

- `id` bigint NOT NULL DEFAULT `nextval('weekly_verses_id_seq'::regclass)`
- `public_id` uuid NOT NULL
- `week_start` date NOT NULL
- `book` integer NOT NULL
- `chapter_start` integer NOT NULL
- `verse_start` integer NOT NULL
- `chapter_end` integer NOT NULL
- `verse_end` integer NOT NULL
- `translation_id` integer
- `created_at` timestamp with time zone NOT NULL DEFAULT `CURRENT_TIMESTAMP`
- `updated_at` timestamp with time zone
- `deleted_at` timestamp with time zone

Constraints:
- `ck_weekly_verse_range` `CHECK ((((book >= 1) AND (book <= 66)) AND (chapter_start >= 1) AND (verse_start >= 1) AND (chapter_end >= chapter_start) AND ((chapter_end > chapter_start) OR (verse_end >= verse_start))))`
- `uq_weekly_verse_week` `UNIQUE (week_start)`
- `weekly_verses_pkey` `PRIMARY KEY (id)`
- `weekly_verses_public_id_key` `UNIQUE (public_id)`
