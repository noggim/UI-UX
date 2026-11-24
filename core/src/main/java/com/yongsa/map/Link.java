package com.yongsa.map;

/** 두 방을 잇는 문 한 쌍. 보통은 통로로 걸어서 잇고, 통로가 없는 이음(심연 계단)은 지나가면 짝 문 앞에 선다. gate 가 있으면 조건을 채워야 열린다. */
public class Link {
    public final Room a, b;
    /** null, 또는 열리는 조건: 던전 ID(LEFT/RIGHT/TOP)  */
    public final String gate;
    public Door doorA, doorB;
    public boolean gateClosed;
    /** 한쪽 길: doorA 로만 지나 doorB 앞에 선다. doorB 는 다른 이음에 속한다. */
    public boolean oneWay;
    public boolean transition;
    /** 두 문 사이에 걸어서 지나는 통로가 있다 (문을 지나도 옮겨 가지 않는다). */
    public Corridor corridor;

    Link(Room a, Room b, String gate) {
        this.a = a;
        this.b = b;
        this.gate = gate;
        this.gateClosed = gate != null;
    }

    public Room other(Room r) {
        return r == a ? b : a;
    }
}
