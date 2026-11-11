-- 사전 등록 유입 경로(링크의 utm_source). 이 컬럼 이전 등록자는 NULL이다.
ALTER TABLE subscribers ADD COLUMN utm_source TEXT;
