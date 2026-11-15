-- 이벤트를 만든 요청의 추적 ID / 발행·수신 로그를 원래 요청과 연결
ALTER TABLE outbox_event
    ADD COLUMN trace_id VARCHAR(64) NULL AFTER payload;
