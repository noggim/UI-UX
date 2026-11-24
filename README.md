# 용사가 다한다

uiux 팀 프로젝트 (최해성 · 박지용 · 김선우). Java 17 + libGDX 2D 게임.

## 함께 작업하기

**최해성의 요청 작업은 `haeseong/game` 브랜치 하나에 누적합니다.** 클로드 통합본과 맵 PR #14, 이야기 그림 PR #15를 모두 포함합니다. 사용자가 따로 요청할 때만 브랜치를 나눕니다. [담당자별 브랜치와 가져오는 방법](docs/BRANCHES.md), [통합 게임 화면](design/haeseong-integration/README.md), [남은 작업](docs/REMAINING_WORK_2026-10-16.md)을 확인하세요.

현재 진행 방향은 [디자인 기준](docs/ART_DIRECTION.md), 작업 절차는 [협업 안내](docs/COLLABORATION.md)에 있습니다. `main`은 검토 후 병합하는 팀 기준 버전입니다.

[최신 인게임 적용본](design/style2-runtime/README.md)은 선택한 2번 스타일을 용사·검·적·소품·지형에 연결하고, 직접 망치질하는 대장간과 작은 확률 패널을 적용했습니다. 큰 미리보기는 기존 상세 그림을 유지합니다. [실제 게임 캡처 보기](design/style2-runtime/index.html) · [검수 기록](design/style2-runtime/QA.md). v3·v4 시안은 비교 기록으로 보존합니다.

## 실행

[자연스러운 던전 구상과 Java 미리보기](design/natural-dungeons/README.md)의 배경 그림 18장과 중앙캠프 그림은 이제 실제 방으로 쓰입니다 (`assets/scenes/natural-v1/`, 바닥 경계 `assets/data/natural-room-scenes.json`). [구상안 / Java 화면 / 경계 비교](design/natural-dungeons/index.html). 더 필요한 그림은 [그림 요청 목록](docs/ART_REQUESTS.md)에 있습니다.

Windows (JDK 17 필요, `JAVA_HOME` 설정):

```
.\gradlew.bat run
```

테스트: `.\gradlew.bat :core:test` · 실행 파일 묶기: `.\gradlew.bat :lwjgl3:dist` (lwjgl3/build/libs 에 jar 생성)

## 조작

| 키 | 동작 |
|---|---|
| WASD / 방향키 | 이동 |
| J | 베기 (누르고 있으면 계속) |
| 스페이스 / 왼쪽 Shift / K | 구르기 (잠깐 무적) |
| E | 상호작용 (대장간, 침대, 모닥불, 광맥 캐기, 포탈, 캠프 바닥 문양) |
| I | 가방 |
| M / Tab | 지도 열기·닫기 |
| 마우스 휠 / + - | 화면 확대·축소 (일반 구간과 보스방을 따로 기억) |
| Esc | 일시정지 메뉴 |
| F1 | 개발용 메뉴 (보스 부활, 광물 무한, 무적, 강화 숙련도) |

**조작 키 바꾸기** (2026-10-27): 타이틀 또는 Esc 메뉴의 **조작 키**에서 동작마다 키를 세 개까지 바꿀 수 있다.
칸을 고르고 Enter(또는 클릭) → 바꿀 키를 누른다. 다른 동작이 쓰던 키면 그쪽에서 빠진다는 안내가 뜨고, Backspace/Delete 는 칸 비우기, Esc 는 취소.
동작마다 "기본", 아래에 "모두 기본값으로"가 있다. Esc·F1·Enter 는 바꿀 수 없다. 바꾼 키는 설정 파일(`settings.json`)에 저장되고,
화면 안내(스킬 칸 위 키, "E  대장간" 같은 상호작용 안내, 오른쪽 위 가방·지도 키)도 바꾼 키로 바뀐다. 위 표는 기본 키다.

## 강화 (2026-10-27)

결과는 **성공(+1) · 대성공(+2) · 유지 · 하락(-1) · 파괴** 다섯 가지다. 대성공은 모든 단계 3%이고, +9 에서는 대성공이어도 +10 까지만 오른다.

| 단계 | 성공 | 대성공 | 유지 | 하락 | 파괴 | 비용 |
|---|---|---|---|---|---|---|
| +0 / +1 / +2 | 92 / 85 / 78 | 3 | 5 / 12 / 19 | 0 | 0 | 1개 |
| +3 / +4 / +5 | 71 / 64 / 57 | 3 | 13 / 16.5 / 20 | 13 / 16.5 / 20 | 0 | 2개 |
| +6 / +7 / +8 / +9 | 50 / 43 / 36 / 29 | 3 | 19 / 22 / 24 / 27 | 21 / 24 / 28 / 31 | 7 / 8 / 9 / 10 | 3개 |

**강화 숙련도** (Lv 0 ~ 20): 모든 검이 함께 쓰는 용사의 실력. 검이 파괴되거나 새 검을 만들어도 그대로 남는다.

- 강화할 때마다 경험치: 성공 20 · 대성공 30 · 하락 30 · 파괴 60 · 유지 0, 여기에 구간 배율 (+0~2 ×0.75, +3~5 ×1.5, +6~9 ×2). 0강에서도 쌓이고, 실패(하락·파괴)에서 더 많이 배운다.
- 다음 레벨까지 필요 경험치 = `20 + 5×Lv + 0.5×Lv²` (20 → 26 → … → 296, 마스터까지 2,590). 초반은 조금 빠르게, 중반은 적당히, 후반은 느리게.
- 하락 확률 × (1 − Lv/17), 파괴 확률 × (1 − Lv/15). 줄어든 만큼은 유지로 가고 성공 확률은 그대로다.
  **Lv 15 부터 파괴 없음, Lv 17 부터 하락 없음** (유지·성공·대성공만). Lv 18/19/20 은 대성공 +1/+2/+3%p (유지에서 옮긴다).
- 대장간 위쪽에 숙련도 막대, 확률 옆에 줄어들기 전 값(괄호), 레벨이 오르면 알림. 세 검을 +6 정도로 키우면 Lv 9, 보통(+6·+8·+10) Lv 12~13, 세 검 모두 +10 이면 Lv 15 안팎. 마스터는 게임 뒤에도 파고드는 목표.
- 값은 모두 `assets/data/balance.json` 의 `enhance.odds` 와 `enhance.mastery` 에 있다.

## 구조

- `core/` 게임 로직과 화면 (`com.yongsa`). 담당별로 폴더를 나눠 서로 같은 파일을 덜 고치게 했다.
  - `map/` **맵 담당**: 방 배치·문·통로·광맥 자리(`WorldMap`, `Room`, `Door` …)와 맵 그리기(`MapPainter`, `PassagePainter`, `RoomGates` …)
  - `enemy/` **보스·몬스터 담당**: `Monster`·`Hazard`·`Projectile`, 잡몹 AI(`MonsterBrain`), 보스 패턴(`BossBrain`), 몬스터·보스 그리기(`EnemyPainter`)
  - `ui/` **UI 담당**: HUD·창·메뉴(`Hud`, `ForgeWindow` …)와 타이틀·메뉴·인트로·스토리·엔딩 화면
  - `audio/` **음악 담당**: 효과음(`Sfx`), 배경음악(`Bgm`, `assets/audio/bgm/<이름>.ogg`)
  - 공용: `model/` 규칙과 게임 진행(`GameSession`), `combat/` 용사·충돌, `gfx/` 그림·빛·알갱이, `config/`, `save/`, `screen/` 게임 화면(`GameScreen`)과 그리기 순서(`WorldRenderer`)
- `lwjgl3/` 데스크톱 실행기
- `assets/data/balance.json` 모든 수치 (체력, 공격력, 강화 확률, 몬스터)
- `assets/data/map.json` 방 배치(그림·크기)와 문 이음, 잠긴 던전 입구 조건
- `assets/data/natural-room-scenes.json` 던전 방 그림의 바닥 경계, 막힌 소품, 출구 자리
- `assets/data/sprites.txt` 픽셀 그림 데이터
- 저장 파일: `%USERPROFILE%\.yongsa_game\save.json`

## 글꼴

나눔스퀘어라운드 (SIL Open Font License, `assets/fonts/NANUM_FONT_LICENSE.txt`).
