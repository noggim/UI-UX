package com.yongsa.model;

/** 던전마다 하나씩 나오는 광물. */
public enum OreType {
    BLACKIRON("흑철석"),
    BLOODMOON("혈월석"),
    SKY("천공석");

    public final String displayName;

    OreType(String displayName) {
        this.displayName = displayName;
    }
}
