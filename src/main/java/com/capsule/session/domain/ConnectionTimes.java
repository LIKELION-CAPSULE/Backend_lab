package com.capsule.session.domain;

/**
 * 프론트가 보내는 연결 누적 시간(초) 4개 (api.md 21 · 22). 0 ≤ present ≤ cameraOn ≤ connected, pip ≤ connected
 *
 * @param connected 연결 시간 (통화 시간)
 * @param present 함께한 시간 (카메라 켜짐 + 얼굴 감지)
 * @param cameraOn 카메라 켠 시간
 * @param pip PIP로 띄운 시간
 */
public record ConnectionTimes(int connected, int present, int cameraOn, int pip) {

    public static final ConnectionTimes ZERO = new ConnectionTimes(0, 0, 0, 0);

    /** 값이 다 있고 순서가 맞고, 연결 시간이 시작부터 지난 시간 이하인지 */
    public static boolean valid(
            Integer connected, Integer present, Integer cameraOn, Integer pip, long elapsedSeconds) {
        return connected != null
                && present != null
                && cameraOn != null
                && pip != null
                && present >= 0
                && pip >= 0
                && present <= cameraOn
                && cameraOn <= connected
                && pip <= connected
                && connected <= elapsedSeconds;
    }
}
