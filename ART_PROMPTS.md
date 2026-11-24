# 아트 생성 기록

Codex 내장 image_gen 도구를 사용했다. PNG는 원본 해상도 및 알파 채널을 보존하여 복사했다. 스프라이트 영역 선택은 런타임 TextureRegion 메타데이터로 처리한다.

## 초기 캐릭터·적·소품 시트 사양 요약

- 용사: 갈색 머리, 붉은 스카프, 남색 튜닉, 가죽 부츠. 정면·후면·측면 대기와 걷기 6포즈. 투명 배경, 별도 무기.
- 적: 박쥐, 해골, 임프, 청록 결정 골렘, 붉은 기사, 보라색 수문장, 금빛 관의 마왕. 각 실루엣을 유지한 상세 RPG 아트.
- 소품: 석조 화로와 모루, 침낭, 모닥불, 흑철·혈월·천공 광맥.
- 초기 무기 시트는 잘림 및 옆 칸 혼입을 발견하여 개별 무기 PNG 8개로 대체했다.

## 최종 생성 지시

### 지형

Production 2D RPG game texture atlas, exactly four equal columns and two equal rows, tightly edge-to-edge with no gaps, no labels. Pure overhead flat material surfaces, no perspective. Top row FLOOR: warm brown gray camp cobblestone with restrained moss; cold blue slate paving for mine; deep desaturated garnet castle flagstones; violet charcoal tower paving with faint worn gold seams. Bottom row matching WALL surfaces: dark camp stone masonry; dark blue craggy rock; desaturated red castle ashlar; purple black basalt bricks. Every swatch fills its rectangle with a repeating flat surface, suitable as tile textures. Beautiful detailed hand-painted dark fantasy materials, small chips, natural irregularity, subdued contrast so characters stand out. Completely opaque, no border, no vignette, no text, no props, no figures, no 3D cubes.

### sword_holy.png

Extract and recreate ONLY ONE sword from reference: top row first, ivory holy sword wrapped in chains. Preserve exact identity, blade geometry, colors, detailed guard and hand-painted fantasy materials. One standalone production game item sprite diagonal from grip bottom-left to tip top-right. Entire sword from pommel to tip visible centered in SQUARE canvas with 15 percent fully transparent margin on every side. No other weapons or stray fragments from neighboring swords. No text, no clouds, no background. Actual zero-alpha transparent background. Extremely clean complete silhouette, intricate professional RPG art. Exactly one sword.

### sword_blackiron.png

Extract and recreate ONLY ONE sword from the reference atlas: top row second, dark blue broad blackiron greatsword, unawakened. Preserve its exact identity, shape, colors, ornate guard and material treatment. Standalone production game item sprite, diagonal from grip bottom-left to tip top-right. Whole sword from pommel to tip visible, centered in a SQUARE canvas with 15 percent fully transparent padding on ALL sides. No other weapons, absolutely no floating fragments from neighboring swords, no text, no clouds, no colored backdrop. True zero-alpha transparent background. Detailed high quality hand-painted RPG item art. This output contains exactly one single sword.

### sword_bloodmoon.png

Extract and recreate ONLY ONE sword from the reference atlas: top row third, curved garnet red bloodmoon saber, unawakened. Preserve its exact identity, shape, colors, ornate guard and material treatment. Standalone production game item sprite, diagonal from grip bottom-left to tip top-right. Whole sword from pommel to tip visible, centered in a SQUARE canvas with 15 percent fully transparent padding on ALL sides. No other weapons, absolutely no floating fragments from neighboring swords, no text, no clouds, no colored backdrop. True zero-alpha transparent background. Detailed high quality hand-painted RPG item art. This output contains exactly one single sword.

### sword_skyfall.png

Extract and recreate ONLY ONE sword from reference: top row fourth, thin fork-tipped ice blue skyfall sword. Preserve exact identity, blade geometry, colors, detailed guard and hand-painted fantasy materials. One standalone production game item sprite diagonal from grip bottom-left to tip top-right. Entire sword from pommel to tip visible centered in SQUARE canvas with 15 percent fully transparent margin on every side. No other weapons or stray fragments from neighboring swords. No text, no clouds, no background. Actual zero-alpha transparent background. Extremely clean complete silhouette, intricate professional RPG art. Exactly one sword.

### sword_holy_awakened.png

Extract and recreate ONLY ONE sword from reference: bottom row first, ivory gold holy sword with winged guard. Preserve exact identity, blade geometry, colors, detailed guard and hand-painted fantasy materials. One standalone production game item sprite diagonal from grip bottom-left to tip top-right. Entire sword from pommel to tip visible centered in SQUARE canvas with 15 percent fully transparent margin on every side. No other weapons or stray fragments from neighboring swords. No text, no clouds, no background. Actual zero-alpha transparent background. Extremely clean complete silhouette, intricate professional RPG art. Exactly one sword.

### sword_blackiron_awakened.png

Extract and recreate ONLY ONE sword from reference: bottom row second, awakened blue crystalline broad blackiron greatsword. Preserve exact identity, blade geometry, colors, detailed guard and hand-painted fantasy materials. One standalone production game item sprite diagonal from grip bottom-left to tip top-right. Entire sword from pommel to tip visible centered in SQUARE canvas with 15 percent fully transparent margin on every side. No other weapons or stray fragments from neighboring swords. No text, no clouds, no background. Actual zero-alpha transparent background. Extremely clean complete silhouette, intricate professional RPG art. Exactly one sword.

### sword_bloodmoon_awakened.png

Extract and recreate ONLY ONE sword from reference: bottom row third, awakened ornate red crescent bloodmoon saber. Preserve exact identity, blade geometry, colors, detailed guard and hand-painted fantasy materials. One standalone production game item sprite diagonal from grip bottom-left to tip top-right. Entire sword from pommel to tip visible centered in SQUARE canvas with 15 percent fully transparent margin on every side. No other weapons or stray fragments from neighboring swords. No text, no clouds, no background. Actual zero-alpha transparent background. Extremely clean complete silhouette, intricate professional RPG art. Exactly one sword.

### sword_skyfall_awakened.png

Extract and recreate ONLY ONE sword from reference: bottom row fourth, awakened violet gold lightning fork sword. Preserve exact identity, blade geometry, colors, detailed guard and hand-painted fantasy materials. One standalone production game item sprite diagonal from grip bottom-left to tip top-right. Entire sword from pommel to tip visible centered in SQUARE canvas with 15 percent fully transparent margin on every side. No other weapons or stray fragments from neighboring swords. No text, no clouds, no background. Actual zero-alpha transparent background. Extremely clean complete silhouette, intricate professional RPG art. Exactly one sword.
