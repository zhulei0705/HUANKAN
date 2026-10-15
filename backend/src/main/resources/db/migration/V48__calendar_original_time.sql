ALTER TABLE official_release_calendar ADD COLUMN planned_time_text text;
ALTER TABLE official_release_calendar ADD COLUMN timezone_basis text NOT NULL DEFAULT 'INSTITUTION_LOCATION_NOT_CONVERTED';
COMMENT ON COLUMN official_release_calendar.planned_time_text IS '原站明确显示的计划时间文本，不以所在地时区擅自换算；非实际发布时间';
