ALTER TABLE app_settings ADD COLUMN title_bar_show_navigation BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE app_settings ADD COLUMN title_bar_actions TEXT NOT NULL DEFAULT '[{"id":"mobile","visible":true},{"id":"library","visible":true},{"id":"settings","visible":true},{"id":"playback","visible":false},{"id":"remote","visible":false}]';
