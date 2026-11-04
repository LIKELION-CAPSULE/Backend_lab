package com.capsule.dialogue.dto;

/** 실험실: 대화방 만들기·고치기. 고칠 때 null인 칸은 그대로. pipeline: SIMPLE · ENGINE */
public record LabSessionRequest(String title, Long characterId, String pipeline, LabConfig config) {}
