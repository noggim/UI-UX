package com.yongsa.config;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/** 시작·엔딩 이야기 그림 장면 (assets/data/story.json). */
public class Story {
    public List<Page> opening = new ArrayList<>();
    public List<Page> ending = new ArrayList<>();

    /** 한 장: assets/story/ 아래 그림 (없으면 글만) 과 아래 자막. */
    public static class Page {
        public String image, caption;
    }

    public static Story parse(String json) {
        return new Gson().fromJson(json, Story.class);
    }
}
