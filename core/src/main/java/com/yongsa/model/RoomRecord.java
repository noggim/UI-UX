package com.yongsa.model;

import java.util.List;

/**
 * 방 하나의 되돌릴 수 있는 상태. 침대를 쓰면 초기화된다 (FR-던전-05).
 * veins 가 null 이면 아직 광맥을 만들지 않은 것이다 (침대 뒤 처음 들어갈 때 만든다).
 */
public class RoomRecord {
    public boolean cleared;
    public boolean visited;
    public List<Vein> veins;
}
