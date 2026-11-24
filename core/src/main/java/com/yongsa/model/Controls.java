package com.yongsa.model;

/** 이번 프레임의 입력. 화면이 키보드에서 읽어 GameSession 에 넘긴다. */
public class Controls {
    public float moveX, moveY;
    public boolean attack, roll, interact;
    /** 누른 스킬 칸 (0=L, 1=U, 2=I, 3=O). 안 눌렀으면 -1. */
    public int skill = -1;

    public void clear() {
        moveX = moveY = 0;
        attack = roll = interact = false;
        skill = -1;
    }
}
