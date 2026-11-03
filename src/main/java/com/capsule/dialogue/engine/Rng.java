package com.capsule.dialogue.engine;

/** 원본 JS의 mulberry32 / hashStr와 비트 단위로 같은 결과를 낸다. 상태는 int 하나라 JSON으로 저장 가능. */
public final class Rng {
    private Rng() {}

    /** state[0]을 갱신하고 [0,1) 난수를 돌려준다. */
    public static double next(int[] state) {
        int a = state[0] + 0x6D2B79F5;
        int t = (a ^ (a >>> 15)) * (1 | a);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        state[0] = a;
        return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** JS: h = (h * 33) ^ code; return h >>> 0 */
    public static long hash(String s) {
        int h = 5381;
        for (int i = 0; i < s.length(); i++) h = (h * 33) ^ s.charAt(i);
        return Integer.toUnsignedLong(h);
    }

    public static int seed(String key) {
        return (int) hash(key);
    }
}
