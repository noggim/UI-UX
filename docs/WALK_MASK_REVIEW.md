# 이동 마스크 전수 대조 — 묶음 M, 2026-10-28

그림에는 길인데 이동하지 못하던 가장자리와, 벽·바위인데 걸을 수 있던 부분을 원화에 맞췄다. 방 29장·통로 9장·연결부 35장, **73개 원화와 마스크를 전부 겹쳐 검토**했고, 24개 마스크의 41개 구간을 수정했다. 나머지 49개는 유지했다.

기준은 `9dc9bc3`의 마스크 초안이다. 같은 브랜치에 이어 올라온 `71bcf6e`의 F1 마스크 파일명 표시도 포함해 검증했다. 작업은 `haeseong/game`과 누적 PR #20에 반영한다. `main`과 중간발표용 `middle-term`에는 별도로 병합하지 않는다.

## 수정 범위

| 종류 | 수정한 마스크 | 바뀐 이동 범위 |
|---|---|---|
| 방 8개 | mine-ore-a/b, citadel-ore-a/north, citadel-corner, tower-ore-a/b, tower-antechamber | 광맥 앞의 흙·포장 바닥, 남쪽 포장 가장자리를 열었다. 광산 바위 받침과 성채 남쪽 벽은 막았다. |
| 통로 4개 | citadel-gallery, citadel-crossing, tower-bridge, camp-trail | 성채 굽은 통로와 캠프 길의 바닥 가장자리, 탑 다리 중앙 착지를 열었다. 다리 뒤 난간 받침은 막았다. |
| 연결부 12개 | mine-side, exit-L1-L2, entry-R2-R7, entry-R3-R4, exit-R3-R5, entry/exit-T1-T2, exit-T3-T4, exit-T3-T5, entry/exit-T5-T7, entry-CAMP-V | 계단 전체 폭과 벽 앞 착지를 열었다. 갱도 지지대, 탑 뒤 난간·기둥, 캠프 모닥불 돌과 계단 옆 바위를 막았다. |

앞쪽 벽은 화면에 보이는 높은 면 전체를 막는 대신 **바닥에 닿는 위치**를 기준으로 판단했다. 가림 레이어나 그림을 새로 만드는 작업은 아니다.

탑 다리 두 곳(`T1.east → T2.west`, `T5.west → T7.east`)의 기존 중심선이 새로 막은 난간을 스치므로 `map.json`의 `painted.route`만 실제 바닥 쪽으로 옮겼다. 문 양끝 좌표, 방 위치·크기·연결, 진행 잠금 조건은 그대로다. T4 동쪽 광물방 확장부는 본바닥과 충분히 겹쳐 발이 걸리는 작은 고립 공간을 없앴다.

원화 PNG 73개의 바이트·크기·이름은 보존했다. 마스크도 원화와 같은 크기이며 불투명한 순수 흑백 PNG다. 회색, 리사이즈, 자동 팽창, 새 그림 생성은 사용하지 않았다. `mapVersion`과 세이브 구조도 바뀌지 않는다.

## 검토 기록과 재현

[walk-mask-review.json](walk-mask-review.json)에 73개 대상의 원화 SHA-256, 규격, 검토 결과와 보정 좌표를 기록했다. `edits`는 왼쪽 위 원점의 정규화 좌표이며 `samples`는 눈으로 확인한 바닥/장애물 기준점 41개다.

저장소 루트에서 Python과 Pillow로 실행한다.

```powershell
# 읽기 전용 대조 시트: 초록 영역은 바닥 마스크, 선은 경계다.
python tools/review_walk_masks.py --output design/walkmask-audit/current
python tools/review_walk_masks.py --output design/walkmask-audit/current --id tower-ore-a

# 이번 검토 좌표를 현재 마스크 위에 다시 적용한다. 이미 반영됐으면 변경 0개.
python tools/apply_walk_mask_review.py

.\gradlew.bat :core:test :lwjgl3:dist
```

`apply_walk_mask_review.py`는 원화 해시가 달라지면 중단한다. 원화를 바꿀 때는 이전 보정 좌표를 그대로 재사용하지 말고 재검토해야 한다. 후속 마스크 보정이 이번 좌표와 겹치면 기록도 함께 갱신한다. `make_walk_masks.py --force`는 **검토 이전 도형으로 되돌리므로** 일반 갱신에 사용하지 않는다. 실수로 초안을 재생성했다면 이번 기록을 재적용한 뒤 전체 이동 테스트를 실행한다.

게임에서는 **F1 → 이동 영역 보기**로 실제 이동 범위를 볼 수 있다. 화면 아래에는 용사 발밑의 방·통로·연결부 마스크 경로와 좌우 반전 여부가 표시된다. 표시되는 모든 마스크가 같은 우선순위는 아니며, 실제 판정은 위에 덮인 연결부와 문 잠금부터 확인한다.

## 검증

- Java 17 `:core:test :lwjgl3:dist`: **145개 테스트 통과, 실패·오류 0**, 배포 JAR 빌드 성공.
- 새 `WalkMaskReviewTest`: 73개 런타임 참조와 검토 기록의 일치, 원화 해시, 원화와 동일한 마스크 크기, 불투명한 순수 흑백, 바닥·장애물 기준점을 확인한다.
- 기존 `DungeonWalkabilityTest`: 방 바닥을 4월드px 간격으로 탐색해 고립된 이동 공간과 출입구 도달 여부를 검사한다. 기존 계단 왕복 이동도 통과한다.
- 기존 `PaintedPassageTest`·`WorldTest`·캠페인 테스트: 통로 20개의 중심선을 2월드px 간격으로 검사하고, 실제 입력 이동 및 전투·진행 잠금을 확인한다.
- 실제 Java 렌더러: 방 28곳·통로 20곳·출입구 52곳의 일반 화면과 발 공간 표시 화면 **200장**을 생성해 대조했다. 기둥·횃불 받침의 추가 보정 뒤 관련 6곳 **12장**을 다시 확인했다. 캡처 위치의 용사 발은 모두 이동 가능한 바닥 안에 있었다.
- 보정 도구 재실행: `Changed 0 collision masks`로 멱등성을 확인했다.

검수 캡처와 임시 분석 자료는 사용자 결정에 따라 로컬 `design/walkmask-audit/`에만 보관한다. GitHub에는 게임용 마스크·경로 데이터·테스트·문서·재현 도구만 올린다. 자동 이동 검증과 고정 위치 렌더 검수이며 전체 캠페인을 사람이 처음부터 끝까지 직접 플레이한 결과는 아니다.
