CREATE INDEX idx_gamification_xp_user_date_xp
    ON gamification_xp_ledger (user_id, event_date, xp);
