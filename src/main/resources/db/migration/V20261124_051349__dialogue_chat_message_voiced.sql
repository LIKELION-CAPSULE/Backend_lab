-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 음성 TTS (schema.sql 2026-11-24, ADR 0034): 이 답장에서 보이스 20을 깎았는지. 재전송 응답의 charged를 처음과 같게 다시 계산할 때 쓴다
-- (음성을 보냈어도 대체 대사 · 잔액 부족이면 깎지 않으므로 "보냈는지"가 아니라 "깎았는지")
ALTER TABLE chat_messages ADD COLUMN voice_charged boolean NOT NULL DEFAULT false;
