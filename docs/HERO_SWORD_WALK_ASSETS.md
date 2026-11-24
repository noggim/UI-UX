# 새 용사 검·걷기 자산 인수인계 — H2 (2026-10-27)

[작업지시서](GPT_HERO_SWORD_WALK.md)의 **S1~S3**를 `haeseong/game`에 반영했다. 이후 사용자가 실제 적용을 요청하여 **새 검 표시·손 추적·걷기 잔상 제거·용사 명암 보정까지 게임에 연결했다.** [현재 런타임 적용 기록](HERO_SWORD_RUNTIME.md)을 함께 읽는다. 아래 좌표 계약과 원본 기록은 계속 유효하다.

## S1 — 검 8종

파일은 [harmony/swords](../assets/sprites/hero-motion/harmony/swords)에 있다.

- `sword_blackiron[_awakened].png`: 흑철·푸른 강철 면, 각성은 청록 문양.
- `sword_bloodmoon[_awakened].png`: 붉은 곡선 날·초승달 손막이, 각성은 붉은 문양.
- `sword_skyfall[_awakened].png`: 가는 하늘색 날·번개, 각성은 밝은 청백 문양.
- `sword_holy[_awakened].png`: 흰 날·금색 손막이, 일반형은 봉인 띠 2개, 각성형은 띠가 사라지고 금색 문양이 드러난다.

각각 **256×256 투명 PNG**, 칼끝 오른쪽 위/손잡이 왼쪽 아래, 기준 각도 45°. 검의 실물 길이는 약 **154원본px → 31.9월드px**, 용사 58월드px의 55%다. `pixelsPerWorld=280/58`을 사용한다. 칸 전체를 이전 40×40월드px로 그리지 않는다.

검은 built-in ImageGen으로 8장 각각 생성했다. 생성 크기 1254×1254는 지시된 축척과 달라, **검만 동일한 가로·세로 배율로 축소하고 투명한 256칸에 가운데 배치**했다. 그리기·색칠·배경 지우기는 생성 도구에서 수행했으며 오프라인 작업은 크기 정규화뿐이다. 구버전 `assets/world/sword_*.png`는 보존했다.

## S2 — 걷기 그림과 기존 계약

| 파일 | 크기 | 사용 |
|---|---:|---|
| `harmony/walk.png` | 1681×936 | 새 앞/뒤 보폭, 반대쪽 발 접지 |
| `harmony/walk-diag.png` | 2048×768 | 등을 편 앞/뒤 대각선 |
| `harmony/walk-side.png` | 1672×941 | 이전 walk 원본 바이트를 보존한 옆 방향 |
| `harmony/stride.png` | 기존 그대로 | 옆 방향의 반대 보폭·통과 자세 |
| `harmony/roll*.png` | 기존 그대로 | 구르기는 그림 교체 없이 손 좌표만 추가 |

걷기 생성 원본은 리사이즈하지 않았다. PNG 실제 크기·해시·프레임 영역을 `hero-motion.json`에 갱신했다. 새 앞/뒤·대각선은 방향마다 한 가지 배율을 적용하므로 포즈별 몸 늘이기가 없다. 기존 옆 방향은 원본별 배율을 유지한다. 서기 높이는 앞/뒤/대각선 58월드px, 기존 옆은 약57.93월드px다. 보폭에 따른 작은 높이 변화는 유지한다.

방향 행은 기존 **남/북/동/남동/북동**, 각 행 6프레임, **서기는 index2**, 왼쪽은 런타임 반전이라는 계약을 유지한다. 앞뒤의 서로 반대 발 접지 자세를 반 주기 간격으로 배치했다. 마지막 돌아오는 자세는 중립 자세를 재사용한다. 생성 시트 마지막 열의 가까운 행 간격은 프레임으로 채택하지 않아 이웃 머리가 잘려 들어오는 문제를 피했다.

발의 가로 기준은 앞에 나온 신발이 아니라 몸 중심 아래에 둔다. 프레임마다 앞발을 따라 몸이 좌우로 미끄러지는 것을 방지한다. 공통 가상 캔버스64×72와 발 기준x32/y68은 그대로다. 충돌·속도·공격 판정·구르기·맵·카메라는 변경하지 않았다.

## S3 — 손과 손잡이 좌표

`assets/data/hero-motion.json`:

- 걷기30 + 구르기30 프레임 모두 `hand: [x,y]`, `handSide: "right"`, `handVisible` 추가.
- 검8종은 루트 `swords` 아래에 파일 경로·실제 크기·SHA-256·`grip`·`pixelsPerWorld`·`angleDegrees`를 기록.
- **모든 좌표는 자르거나 반전하기 전 원본 PNG 전체의 왼쪽 위가(0,0)**. 프레임 내부 상대 좌표나 월드 좌표가 아니다.
- `hand`는 그림의 오른손 장갑 중심. `grip`은 손잡이를 쥐는 가운데이며 손잡이 맨 끝 장식(pommel)이 아니다.
- 몸에 가려진 구르기 손도 좌표는 제공하지만 `handVisible=false`다. 이 값은 해부학적 추정점이므로 구르기 중 검을 다시 표시하는 근거로 쓰지 않는다. 기존처럼 몸을 마는 동안 검은 숨긴다.
- 왼쪽 이동은 그림과 손 위치를 함께 반전한다. 그림을 반전한 뒤에도 원본 오른손의 대응점을 따라간다.

Claude C3 연결에 필요한 기본식 (`squash`는 공격 시 기존 몸 변형):

```text
mirror = facing.leftward ? -1 : 1
handWorldX = hero.x + mirror * (hand.x - foot.x) / frame.pixelsPerWorld * (2 - squash)
handWorldY = hero.y + (foot.y - hand.y) / frame.pixelsPerWorld * squash + heroBob

swordWidth  = sword.width  / sword.pixelsPerWorld
swordHeight = sword.height / sword.pixelsPerWorld
originX = grip.x / sword.pixelsPerWorld
originY = (sword.height - grip.y) / sword.pixelsPerWorld
drawPosition = (handWorldX - originX, handWorldY - originY)
rotation = heldOrAttackAngle - sword.angleDegrees
```

검도 용사와 같은 mipmap/linear 필터로 축소한다. 손 좌표는 **실제로 그린 자세와 같은 프레임**에서 가져온다. C1에서 걷기 프레임 혼합을 제거한 뒤 적용해야 두 장의 몸 사이에 검만 한 자세를 따라가는 현상이 생기지 않는다. 위/위 대각선에서의 몸 뒤 그리기 순서는 기존 규칙을 따른다. 성검의 새 일반형에는 봉인 띠가 있으므로 기존 임시 사슬을 중복해서 얹지 않는다.

**C3 검 기울기:** 기존 고정 손 위치용 `restAngle`은 실제 오른손에 붙이면 칼날이 얼굴을 가로질렀다. 적용본은 `HeroMotion.attachedSwordAngle`로 정면/앞 오른쪽 대각선을 110°, 반전한 앞 왼쪽은 70°로 조정했다. 손 좌표를 반대편으로 옮기지 않았다. 이전 로컬 연결 미리보기의 각도 문제는 현재 적용본에서 해결했다.

## 자산 제작 당시 검증 (런타임 연결 전)

- Java17 `:core:test :lwjgl3:dist`: **131개 테스트, 실패0/오류0**, 배포 JAR 빌드 성공.
- 원본 크기·해시, 프레임 잘림, 발 기준, 동일 가로/세로 배율 등 기존129개 검증 유지.
- 추가2개: 60개 손 좌표의 영역·가시 픽셀 검증, 검8종의 투명 여백·해시·손잡이·표시 길이 검증. 가려진 손은 영역 검증만 수행한다.
- 검 PNG가 바뀌어도 테스트가 다시 실행되도록 Gradle 자산 입력에 하위 `swords/`도 포함했다.
- 게임 화면 비교와 별도의 손잡이 연결 검수용 미리보기는 로컬 `design/hero-sword-walk-h2/`, 원본 캡처는 `build/h2-captures/`에 보관한다. 검수는 별도 저장 경로를 사용한다.
- CAMP/L3/R1/T3 × 8방향에서 서기·걷기·공격을 비교했다. 전/후/연결 미리보기 각각 768프레임이다. 실제 LWJGL 렌더러를 사용하되 위치를 고정하고 동작 타이머를 지정한 시각 검수이며, 전체 게임 플레이 테스트는 아니다. 비교표·방향표·동영상 GIF와 개수 검증 기록은 `design/hero-sword-walk-h2/review/`에 있다.
- 연결 미리보기는 실제 렌더러의 로컬 복사본에 **C1/C3만 시험 적용**한 자료다. 배포 코드에는 포함하지 않았다. 기본 게임의 검 교체·손 추적·잔상 제거가 완료됐다는 증거로 쓰지 않는다.
- 생성 프롬프트·선택한 원본 해시·정규화 방식: [생성 기록](hero-sword-walk-generation.json). 새 디자인/검수 캡처를 Git에 올리지 않는 기존 결정에 따라 런타임 자산과 문서·테스트만 커밋한다.

## 후속 연결 상태

C1 걷기 혼합 제거, C2 윤곽/명암 보정, C3 새 검과 손 좌표 연결을 사용자 요청에 따라 완료했다. C4 임시 위치 조정은 C3로 대체했다. 예전 로컬 미리보기 대신 [현재 게임 검증](HERO_SWORD_RUNTIME.md)을 기준으로 확인한다.
