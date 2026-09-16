ALTER TABLE subscribers ADD COLUMN referral_bonus INTEGER NOT NULL DEFAULT 0;
UPDATE subscribers SET referral_bonus = 1 WHERE referrer_id IS NOT NULL;
ALTER TABLE subscribers ADD CONSTRAINT chk_subscribers_referral_bonus
    CHECK ((referrer_id IS NULL AND referral_bonus = 0)
        OR (referrer_id IS NOT NULL AND referral_bonus = 1));
ALTER TABLE subscribers ADD CONSTRAINT chk_subscribers_no_self_referral
    CHECK (referrer_id IS NULL OR referrer_id <> id);
