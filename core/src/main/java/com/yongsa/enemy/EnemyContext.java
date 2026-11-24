package com.yongsa.enemy;

import com.yongsa.combat.HeroActor;
import com.yongsa.config.Balance;
import com.yongsa.map.WorldMap;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.Rules;

import java.util.List;
import java.util.Random;

/**
 * 몬스터·보스 AI 가 같이 쓰는 게임 상태. GameSession 의 목록과 설정을 그대로 가리킨다
 * (복사가 아니라 같은 객체라서 여기서 바꾸면 게임에 바로 반영된다).
 */
abstract class EnemyContext {
    final GameSession g;
    final HeroActor hero;
    final List<Monster> monsters;
    final List<Projectile> projectiles;
    final List<Hazard> hazards;
    final List<GameEvent> events;
    final WorldMap map;
    final Balance balance;
    final GameState state;
    final Rules rules;
    final Random random;
    final int T;

    EnemyContext(GameSession g, List<GameEvent> events) {
        this.g = g;
        this.hero = g.hero;
        this.monsters = g.monsters;
        this.projectiles = g.projectiles;
        this.hazards = g.hazards;
        this.events = events;
        this.map = g.map;
        this.balance = g.balance;
        this.state = g.state;
        this.rules = g.rules;
        this.random = g.random;
        this.T = balance.tileSize;
    }

    int tileX(float px) {
        return (int) Math.floor(px / T);
    }

    int tileY(float py) {
        return (int) Math.floor(py / T);
    }

    float center(int tile) {
        return tile * T + T / 2f;
    }

    void hitHero(int base, float mult, float fromX, float fromY) {
        g.hitHero(base, mult, fromX, fromY);
    }
}
