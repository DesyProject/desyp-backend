-- 휴대전화번호 수집을 중단한다. V5의 UNIQUE 제약(별도 인덱스 없음)을 먼저 지우고 컬럼을 삭제한다.
ALTER TABLE subscribers DROP CONSTRAINT uq_subscribers_phone_number;
ALTER TABLE subscribers DROP COLUMN phone_number;
