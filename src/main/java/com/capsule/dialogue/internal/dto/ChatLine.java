package com.capsule.dialogue.internal.dto;

/** LLM에 넘기는 대화 한 줄. sender: USER · CHARACTER */
public record ChatLine(String sender, String content) {}
