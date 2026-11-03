package com.capsule.content.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "live2d_models")
class Live2dModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String storagePrefix;
    String modelJsonPath;
    BigDecimal scale;

    @Column(name = "offset_x") // 끝 글자가 대문자면 이름 규칙이 _를 안 넣음
    int offsetX;

    @Column(name = "offset_y")
    int offsetY;
}
