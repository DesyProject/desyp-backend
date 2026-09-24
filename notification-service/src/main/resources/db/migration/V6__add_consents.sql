-- 기존 행은 개인정보 수집 동의가 필수였고 알림 수신 동의는 받지 않았다.
ALTER TABLE subscribers ADD COLUMN privacy_agreed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subscribers ADD COLUMN marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE;
