package com.yongsa.gfx;

/** 깊이 정렬 목록에 넣는 곳: 발 위치 y 가 낮을수록(아래쪽일수록) 앞에 그린다. 맵·적·용사 그리기가 같이 쓴다. */
public interface DepthSink {
    void add(float y, Runnable draw);
}
