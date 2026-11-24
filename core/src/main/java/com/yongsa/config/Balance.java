package com.yongsa.config;

import com.google.gson.Gson;

import java.util.List;

/**
 * 게임의 모든 수치. assets/data/balance.json 을 그대로 읽는다 (NFR-06).
 * 필드 이름은 JSON 키와 같다.
 */
public class Balance {
    public int tileSize;
    public HeroCfg hero;
    public AttackCfg attack;
    public List<WeaponDef> weapons;
    public EnhanceCfg enhance;
    public MiningCfg mining;
    public MonsterCfg monster;
    public List<DungeonDef> dungeons;
    public MonsterDef finalBoss;
    public RewardCfg reward;
    public PotionCfg potion;
    public VillageCfg village;

    public static Balance parse(String json) {
        return new Gson().fromJson(json, Balance.class);
    }

    public WeaponDef weapon(String id) {
        for (WeaponDef w : weapons) if (w.id.equals(id)) return w;
        throw new IllegalArgumentException("없는 무기: " + id);
    }

    public DungeonDef dungeon(String id) {
        for (DungeonDef d : dungeons) if (d.id.equals(id)) return d;
        throw new IllegalArgumentException("없는 던전: " + id);
    }

    /** 그 광물로 만드는 무기 (던전마다 하나). */
    public WeaponDef weaponForOre(String ore) {
        for (WeaponDef w : weapons) if (ore.equals(w.ore)) return w;
        return null;
    }

    public static class HeroCfg {
        public int baseMaxHp;
        public int[] maxHpAfterBosses;
        public float moveSpeedTiles;
        public float invulnAfterHit;
        public float knockbackTiles;
        public float campRegenFractionPerSec;
    }

    public static class AttackCfg {
        public float basicMultiplier, basicCooldown, basicRangeTiles, basicArcDegrees;
        /** 구르기: 짧게 빠르게 이동하고 그 앞부분 동안은 맞지 않는다. 공격하지는 않는다. */
        public float rollDistanceTiles, rollTime, rollInvulnTime, rollCooldown;
        public float damageSpreadMin, damageSpreadMax;
        /** 이 거리 안에 몬스터가 있으면 용사가 가장 가까운 몬스터를 자동으로 바라본다 (0 이면 끔). */
        public float autoFaceTiles;
    }

    /** 던전 밖 마을 (선우 버전). 마을 사람 자리는 마을 그림 기준 (0~1). */
    public static class VillageCfg {
        public String name;
        /** 마왕 전에는 이만큼 독백하며 되돌아선 다음에야 나갈 수 있다. */
        public int exitRefusals;
        public List<String> exitLines;
        public String giveUpLine, arriveTrappedLine, trappedLine;
        public List<Villager> villagers;
    }

    public static class Villager {
        public String kind, name;
        public float[] at;
        public List<String> freeLines;
    }

    /** 보스 보물상자: 잡을 때마다 그 던전 광물을 chestOreMin~chestOreMax 개. */
    public static class RewardCfg {
        public int chestOreMin, chestOreMax, bossChestPotions;
        /** 잡몹방을 비웠을 때 상자가 나올 확률과 그 안의 광물·포션. */
        public float mobChestChance;
        public int mobChestOreMin, mobChestOreMax, mobChestPotions;
    }

    /** 회복 포션 (던전 상자에서만 나온다). */
    /** 회복 포션: 상자에서 바닥에 떨어지고, 지나가며 주우면 바로 회복한다 (들고 다니지 않는다). */
    public static class PotionCfg {
        public float healRatio, pickupRadiusTiles;
    }

    public static class WeaponDef {
        public String id, name, ore;
        public int baseAttack, perLevel, craftCost;
        public boolean enhanceable, holy;
        public String awakenedName, abilityName, abilityDesc;
        /** 검마다 하나 있는 액티브 스킬 (L·U·I 칸, 성검은 O). 없으면 null (봉인된 성검). */
        public SkillDef skill;
    }

    /**
     * 검 스킬. enhance.skillUnlockLevel 강화부터 쓸 수 있고, 각성하면 awakened* 값으로 강해진다.
     * kind: cleave(땅 가르기 충격파) · crescent(날아가는 초승달 검기) · strike(가까운 적에게 낙뢰) · beam(큰 빛의 검기).
     */
    public static class SkillDef {
        public String name, desc, awakenedDesc, kind;
        public float cooldown, multiplier, awakenedMultiplier = 1f;
        /** 사거리, 검기 폭(낙뢰는 터지는 반지름), 날아가는 빠르기 (타일 단위). */
        public float rangeTiles, widthTiles, speedTiles;
        /** 검기 줄 수 또는 낙뢰 수, 각성하면 awakenedCount. 여러 줄이면 spreadDegrees 간격으로 퍼진다. */
        public int count = 1, awakenedCount = 1;
        public float spreadDegrees;
        /** 맞은 잡몹 기절 시간, 맞힌 적마다 회복하는 최대 체력 비율. */
        public float stunSeconds, healRatio;
    }

    public static class EnhanceCfg {
        public int maxLevel;
        /** 단계(+0~+9)마다 강화 한 번의 확률 (%). 숙련도 0 기준. */
        public List<OddsRow> odds;
        public List<Band> bands;
        public MasteryCfg mastery;
        public float destroyRefundRatio;
        public int dismantleRefund;
        public float hammerSeconds, awakenSeconds;
        /** 이 강화 단계부터 검 스킬 칸이 열린다. 성검은 깨어나면 바로. */
        public int skillUnlockLevel;
    }

    public static class Band {
        public int fromLevel, toLevel, cost;
        /** 이 구간에서 강화할 때 얻는 숙련도 경험치 배율. */
        public float expMultiplier = 1f;
    }

    /** 한 단계의 확률 (%): 성공 +1, 유지, 하락 -1, 파괴. (대성공은 2026-11-07 플레이 피드백으로 없앴다) */
    public static class OddsRow {
        public int level;
        public double success, keep, down, destroy;
    }

    /**
     * 강화 숙련도 (2026-10-27): 모든 검이 함께 쓰는 용사의 대장장이 실력.
     * 레벨이 오를수록 하락·파괴 확률이 줄고(줄어든 만큼 유지), 마지막에는 성공이 조금 늘어난다.
     */
    public static class MasteryCfg {
        public int maxLevel;
        /** 이 레벨에서 하락·파괴 확률이 0이 된다 (그 전까지는 레벨에 비례해 줄어든다). */
        public int downZeroLevel, destroyZeroLevel;
        /** 이 레벨부터 유지 몫에서 성공으로 옮기는 %p. */
        public List<SuccessBonus> successBonus;
        /** 결과마다 얻는 기본 경험치 (구간 배율을 곱한다). 유지에도 쌓인다. */
        public int expSuccess, expKeep, expDown, expDestroy;
        /** 다음 레벨까지 필요 경험치 = needBase + needLinear×Lv + needSquare×Lv² (반올림). */
        public float needBase, needLinear, needSquare;
    }

    public static class SuccessBonus {
        public int level;
        public double percent;
    }

    public static class MiningCfg {
        public int hitsToBreak;
        /** 방 하나의 광맥 수 (그림 속 광석 자리 수를 넘지 않는다). 보스를 잡은 던전은 cleared 값. */
        public int veins, veinsCleared;
        /** 방 하나에서 나오는 광석 합계. 광맥 수로 나눠 담는다. */
        public int oreTotalMin, oreTotalMax, oreTotalMinCleared, oreTotalMaxCleared;
        public int firstOreRoomGuarantee;
    }

    public static class MonsterCfg {
        public float detectTiles, attackReachTiles, telegraphSeconds, attackCooldown, knockbackTiles;
        public float bossSlamTelegraph, bossSlamRadiusTiles, bossSlamMultiplier;
        /** 방에 나타난 뒤 움직이기 시작할 때까지 (마법진이 보이는 시간). */
        public float spawnSeconds;
        /** 박쥐: 주위를 돌다가 일직선으로 덮친다. */
        public float swoopTelegraph, swoopDistanceTiles, swoopTime, swoopRecover, orbitTiles;
        /** 박쥐 콩알탄: 공격할 차례에 pelletChance 확률로 덮치는 대신 작고 빠른 탄 하나를 뱉는다. */
        public float pelletChance, pelletTelegraph, pelletSpeedTiles, pelletRadiusScale, pelletMultiplier;
        /** 박쥐 떼 덮치기: 한 마리가 덮칠 때 packChance 확률로 근처 박쥐 하나가 packDelay 초 늦게 따라 덮친다. */
        public float packChance, packRangeTiles, packDelay;
        /** 해골병: 휘두르며 앞으로 한 발 내딛는다. */
        public float lungeTiles;
        /** 해골병 방패: guardUp 초 들고 guardDown 초 내린다. 드는 동안 정면 피해 감소, 천천히 돈다, 느리게 걷는다. */
        public float guardUp, guardDown, guardReduction, guardTurn, guardSpeed;
        /** 해골병 부활: 처음 쓰러지면 reviveSeconds 뒤 최대 체력의 reviveHpRatio 로 일어난다. */
        public float reviveSeconds, reviveHpRatio;
        /** 마족병: 떨어져서 불덩이를 쏜다. 가까이 오면 순간이동으로 물러난다. */
        public float castTelegraph, castCooldown, castRangeTiles, keepAwayTiles, blinkCooldown;
        /** 마족병: triEvery 번째 사격마다 세 갈래, flameChance 확률로 불덩이 대신 용사 발밑 불기둥, 순간이동 직후 blinkShotTelegraph 만에 한 발. */
        public int triEvery;
        public float flameChance, flameRadiusTiles, flameWarning, flameMultiplier, blinkShotTelegraph;
        public float projectileSpeedTiles, projectileRadius;
        /** 보스 고유 패턴. */
        public float bossPatternGap;
        public float chargeTelegraph, chargeSpeedTiles, chargeMaxTiles, chargeStun, chargeMultiplier;
        public int comboHits;
        public float comboTelegraph, comboStepTiles;
        public int volleyCount;
        public float volleySpreadDegrees, volleyTelegraph;
        public int ringCount;
        public int summonCount;
        /** 체력이 이 비율 아래로 내려가면 2페이즈 (패턴이 빨라지고 하나가 더 붙는다). */
        public float phase2HpRatio, phase2Speed;
        /** 연속 돌진: 매번 다시 조준해서 rushCount 번 달려든다. */
        public int rushCount;
        public float rushTelegraph;
        /** 연속 돌진의 마지막 돌진이 끝나면 이만큼 휘청인다 (선우 버전 좀비킹). */
        public float rushStun;
        /** 연속 돌진 첫째·둘째는 벽에서 이만큼 앞에서 멈춘다 (부딪히지 않는다). 셋째는 벽까지 달려가 부딪힌다. */
        public float rushWallMarginTiles;
        /** 해골 사제 뼈 가시 (선우 버전 리치): 용사 발밑 하나 + 주변 몇 개. */
        public int spikeCount;
        public float spikeRadiusTiles, spikeSpreadTiles, spikeWarning, spikeMultiplier;
        /** 실명: 뼈 가시·소환을 쓸 때 화면이 어두워진다. 한 번 걸면 쿨타임 동안 다시 안 건다. */
        public float blindSeconds, blindCooldown;
        /** 마왕 척추 채찍: 앞쪽 부채꼴을 한쪽 끝에서 다른 끝으로 휩쓴다. */
        public int whipLines;
        public float whipArcDegrees, whipRadiusTiles, whipWarning, whipSweep, whipMultiplier;
        /** 마왕 2페이즈 눈알: 뜨는 순간 그쪽을 보고 있으면 지금 체력의 절반을 잃고 잠깐 굳는다. */
        public int eyeCount;
        public float eyeGap, eyeWarning, eyeAngleDegrees, eyeStun, eyeDistanceTiles;
        /** 낙석: 용사가 있던 자리마다 차례로 예고 뒤 떨어진다. */
        public int rockCount;
        public float rockInterval, rockWarning, rockRadiusTiles, rockMultiplier;
        /** 방패: 최대 체력의 shieldRatio 만큼 막이 생기고, 깨기 전엔 보스 체력이 닳지 않는다. 깨지면 휘청. */
        public float shieldRatio, shieldStagger;
        /** 피웅덩이: 점점 넓어지는 웅덩이. 밟으면 초당 최대 체력의 poolDamageRatio + 둔화. 이어서 창 세 줄. */
        public int poolCount;
        public float poolStartRadiusTiles, poolMaxRadiusTiles, poolGrowTime, poolLife, poolDamageRatio;
        public float slowRatio, slowSeconds;
        public int spearCount;
        public float spearDelay, spearWarning, spearWidthTiles, spearLengthTiles, spearMultiplier, spearSpreadDegrees;
        /** 혈월의 기사 창 찌르기: 멈춰 선 채 spearStandVolleys 번, spearStandInterval 초마다 창 세 줄. 움직이면 남은 창은 사라진다. */
        public int spearStandVolleys;
        public float spearStandInterval, spearStandWarning;
        /** 낙뢰: 용사 자리에 차례로 번개. */
        public int strikeCount;
        public float strikeInterval, strikeWarning, strikeRadiusTiles, strikeMultiplier;
        /** 회전 화염: 제자리에서 돌며 두 갈래로 불덩이를 뿜는다. */
        public float spinMin, spinMax, spinRate, spinTurnSpeed, spinProjectileSpeedTiles, spinMultiplier;
        /** 마왕 2페이즈 촉수: 용사 자리와 그 둘레에 예고 뒤 솟는다. */
        public int tentacleCount;
        public float tentacleRingTiles, tentacleRadiusTiles, tentacleWarning, tentacleMultiplier;
        /** 골렘 지진 파동 (연속 내려찍기가 떨어질 때마다): 찍은 자리 둘레 고리 quakeWaves 겹이 quakeGap 간격으로 안에서 밖으로 터진다. */
        public int quakeWaves;
        public float quakeWaveTiles, quakeGap, quakeMultiplier;
        /** 골렘 2페이즈 바위 던지기: 용사 자리에 큰 바위, 떨어지면 파편이 사방으로 튄다. */
        public float boulderRadiusTiles, boulderWarning, boulderMultiplier;
        public int shardCount;
        public float shardSpeedTiles, shardMultiplier, shardLife;
        /**
         * 골렘 2페이즈 연속 내려찍기: 용사 쪽으로 poundCount 번 내려찍는다. 찍을 때마다 지진 파동이 퍼지고,
         * 다음 내려찍기는 앞 파동의 첫 고리가 사라지는 순간(quakeGap 뒤) 떨어진다. 끝나면 poundRecover 초 숨을 고른다.
         */
        public int poundCount;
        public float poundStartTiles, poundStepTiles, poundRadiusTiles, poundRadiusGrowTiles, poundWarning, poundMultiplier, poundRecover;
        /** 혈월의 기사 회전 베기: 보스 둘레 원을 예고 뒤 한 번에 벤다. */
        public float spinSlashRadiusTiles, spinSlashWarning, spinSlashMultiplier;
        /** 혈월의 기사 혈월 검기: 크고 붉은 초승달 검기 여러 줄을 부채꼴로 날린다. */
        public int crescentCount;
        public float crescentSpreadDegrees, crescentSpeedTiles, crescentRadiusScale, crescentMultiplier;
        /** 뒤로 도약 (cornerLeap 보스): 구석에 몰리면 멀리 뛰어 물러나고 착지한 자리에 충격파. */
        public float leapTime, leapLandRadiusTiles, leapLandMultiplier;
        /** 수문장 번개 감옥: 용사 둘레 사각형 번개 벽이 여러 번 번쩍이고 가운데가 터진다. 한쪽 벽만 늦게 터진다. */
        public float cageHalfTiles, cageWidthTiles, cageWarning;
        public int cagePulses;
        public float cagePulseGap, cageCoreWarning, cageOpenDelay, cageMultiplier, cageCoreMultiplier;
        /** 수문장 유도 마탄: 초당 homingTurn 라디안까지 용사 쪽으로 방향을 튼다. homingStopTiles 안으로 들어오면 더 꺾지 않고, 구르기로 피하면 사라진다. */
        public int homingCount;
        public float homingSpreadDegrees, homingSpeedTiles, homingTurn, homingLife, homingStopTiles;
        /** 마왕 1페이즈 어둠 구체: 느린 큰 구체가 orbFuse 초 뒤(또는 벽에 닿으면) 터지며 사방으로 탄을 뿌린다. */
        public float orbSpeedTiles, orbRadiusScale, orbFuse, orbShardMultiplier;
        public int orbBurst;
        /** 마왕 1페이즈 낙인: 용사를 따라다니다 멈추고, 잠시 뒤 그 자리에서 폭발한다. */
        public float brandFollow, brandLock, brandRadiusTiles, brandMultiplier;
        /** 마왕 1페이즈 (noMeleePhase1): 기본 근접 공격 대신 용사와 이만큼 떨어져서 패턴만 쓴다. */
        public float demonKeepTiles;
        /** 마왕 2페이즈 최후의 발악: 체력이 이 비율 아래면 화면이 어두워지고 패턴 간격이 줄어든다. */
        public float lastStandHpRatio, lastStandGapMultiplier;
        /** 구석 몰이 방지: 벽 가까이에서 용사가 cornerTime 초 넘게 붙어 있으면 순간이동하고 세 갈래 탄. */
        public float cornerTime, cornerCooldown;
        /** 마왕: 처음 쓰러지면 체력이 이만큼 다시 차서 2페이즈 (변신하는 동안은 무적). */
        public float finalPhase2HpMultiplier, finalPhase2AttackMultiplier, phaseShiftSeconds;
        /** 보스를 잡은 던전의 잡몹: 흡수하지 못한 힘이 퍼져 강해진다. */
        public float clearedMobHpMultiplier, clearedMobAttackMultiplier;
    }

    public static class DungeonDef {
        public String id, name, ore, recommended;
        public MonsterDef mob, boss;
        public int mobsPerRoom;
    }

    public static class MonsterDef {
        public String name, sprite;
        public int hp, attack;
        public float speedTiles;
        /** 잡몹 행동: melee(기본), swoop(박쥐), brute(해골병), caster(마족병). */
        public String ai;
        /** 보스 패턴 이름: slam, charge, rush, combo, volley, ring, summon, rockfall, shield, bloodpool, lightning, firespin, tentacle, bonespike, whip, eyes, boulder, pound, spearstand, spinslash, crescent, cage, homing, darkorb, brand. */
        public List<String> patterns;
        /** 2페이즈에 더해지는 패턴 (없으면 빠르기만 한다). 마왕은 2페이즈에 이 목록으로 바뀐다. */
        public List<String> phase2Patterns;
        /** 잡몹: 공격 예고 시간과 공격 뒤 쉬는 시간 (0이면 monster 공통 값). */
        public float telegraph, cooldown;
        /** 보스: 구석에 몰리면 순간이동해서 빠져나간다. */
        public boolean cornerBlink;
        /** 보스: 구석에 몰리면 순간이동 대신 뒤로 크게 도약해서 빠져나간다 (혈월의 기사). */
        public boolean cornerLeap;
        /** 보스 소환: 0보다 크면 summonCount ~ summonCountMax 마리 중 무작위 (탑의 수문장 2~3마리). */
        public int summonCountMax;
        /** 잡몹(해골병): 방패를 들었다 내렸다 한다, 한 번 다시 일어난다. */
        public boolean guard, revive;
        /** 마왕: 1페이즈에서는 기본 근접 공격을 하지 않고 거리를 둔다 (monster.demonKeepTiles). */
        public boolean noMeleePhase1;
        /** 보스 소환: 어느 던전의 잡몹을 몇 마리 부르나 (없으면 자기 던전 잡몹, monster.summonCount). */
        public String summonFrom;
        public int summonCount;
    }
}
