# 0035 알림은 user 모듈 테이블 하나, 다른 모듈은 계약 호출 · 이벤트로 적는다

- 결정
  - 알림 모아 보기(SCR-63, api.md 43 · 44)는 user 모듈의 `notifications` 테이블 하나에 쌓는다. 종류: STAGE_UP(친밀도 단계 상승) · ATTENDANCE(출석 크레딧) · REFERRAL_REWARD(초대 보상)
  - 적는 쪽
    - session(연결 끊기 22에서 단계가 오르면): user 계약 `NotificationCommands.stageUp`을 부른다 (문구는 user가 만든다). session은 이미 user api를 쓴다
    - credit(출석 지급): 계약 호출 대신 `AttendanceGrantedEvent`(credit api)를 내고 user가 받아 적는다. user가 이미 credit을 부르므로(8번 credits) credit → user 호출은 순환이 된다
    - user(초대 보상): 안에서 바로 적는다
  - 같은 일을 두 번 적지 않게 `dedup_key`(출석 날짜, 연결 id, 초대 id + 쪽)에 부분 유니크 인덱스
  - 알림 수는 내 정보(8) `unreadNotifications`로도 준다 (GNB 벨 점)
- 이유
  - 알림은 "이 사용자에게 보여 줄 것"이라 사용자 모듈에 둔다. 모듈마다 자기 알림을 따로 두면 목록 하나를 만들려고 여러 모듈을 모아 정렬해야 한다
  - 적을 때 같은 트랜잭션에 들어가므로(계약 호출 · 동기 이벤트) 정산이 롤백되면 알림도 남지 않는다
- 버린 대안
  - 읽을 때 모으기(크레딧 내역 · 정산 기록에서 만들어 보여 주기): 저장은 없지만 읽음 표시를 둘 곳이 없고, 매번 여러 모듈을 불러야 한다
  - 새 모듈 `notification`: 테이블 하나 · API 두 개라 모듈을 늘릴 만큼 크지 않다(zones.conf · ModularityTests가 늘어남). 커지면 그때 뺀다
  - 웹 푸시 · 실시간(SSE): 베타 화면은 벨을 눌러 보는 목록뿐이다
- 대가
  - 운영 공지(예: 남학생 메이트 공개)처럼 모든 사용자에게 가는 알림은 행을 사람 수만큼 만들어야 한다. 공지 화면 · 절차가 정해지면 별도 테이블을 검토한다 (확정 필요)
  - 알림을 새로 더할 때마다 적는 쪽 모듈 코드가 바뀐다
