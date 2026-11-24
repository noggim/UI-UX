# 남은 작업 — 2026-10-15

확인 기준: `claude/team-integration-m9u9aa` (`95ce69a`) + `codex/map-continuity` 개선안. 통합 브랜치에 이미 들어간 작업과 별도 브랜치 초안을 구분한다.

| 구분 | 현재 상태 | 다음 할 일 |
|---|---|---|
| 맵·통로 | 이번 개선안 구현·검증 완료. 방 10곳 정렬, 꺾임 37→13, 새 재질 4장, 93개 테스트 통과 | 통합 브랜치에 PR을 합친 뒤 실제 플레이 흐름에서 최종 확인 |
| A: 보스 3종 | 골렘·기사·마왕 시트가 기준 통합 브랜치에 이미 반영됨 (PR #13) | 새 그림을 다시 만들 필요 없음 |
| B: 이야기 그림 | `codex/story-sprites`의 `9aafafa`에 **초안 9개** 보관: 마왕 2페이즈, 주민 6명, 수정 감옥, 잠자는 용사 | 프레임별 실제 렌더링 검수 후 별도 PR. 주민·수정·잠자는 용사는 기존 파일 경로로 교체 가능. **2페이즈 마왕은 코드 연결도 필요** |
| C: 전투·보상 | 눈알 패턴 효과, 던전별 상자 3종이 아직 미완료 | 눈알 4프레임(192×32), 상자 각 3프레임(132×40) 제작·검수. 눈알은 코드 연결 필요 |
| D: 앞쪽 가림 | 기존 11장 반영됨. 현재 사용하는 장면 중 앞쪽 레이어 파일 없는 곳은 14곳 | 캠프와 성채·탑의 전투방을 우선 제작해 용사가 앞벽 위로 보이는 부분을 줄이기. 마을은 실제 가릴 물체가 필요한지 먼저 판단 |
| 선택: 광물방 | 광석이 적거나 흐린 3곳이 남음 | `mine-ore-b`, `citadel-ore-north`, `tower-ore-b`의 광석을 또렷하게 그린 뒤 `oreSpots` 재검수 |
| 기획 대기 | 검 스킬 효과, 탑 보스 새 디자인 | 보스 패턴을 사용하는 스킬 방향을 확정한 뒤 제작 |

앞쪽 레이어가 없는 실제 사용 장면: `central-camp`, `mine-junction`, `mine-antechamber`, `citadel-ore-b`, `citadel-corner`, `citadel-mob-c`, `citadel-ore-north`, `citadel-mob-b`, `tower-mob-c`, `tower-ore-b`, `tower-ore-a`, `tower-mob-b`, `tower-antechamber`, `village`.

추천 순서는 **이번 맵 PR 통합 → B 초안 검수·연결 → 상자와 눈알 → 앞쪽 가림 → 전체 플레이·밸런스 점검**이다. 전체 플레이에서는 새 게임→세 던전→마왕 2페이즈→주민 구출/엔딩, 사망·침대 초기화, 저장 후 재개를 확인해야 한다. 이번 맵 테스트/화면 캡처는 그 전체 플레이를 완료했다는 뜻이 아니다.

상세 제작 규격은 [ART_REQUESTS.md](ART_REQUESTS.md), 기존 작업 묶음은 [GPT_WORK_ORDER.md](GPT_WORK_ORDER.md), 맵 변경 근거와 화면은 [map-continuity](../design/map-continuity/README.md)를 참조한다.
