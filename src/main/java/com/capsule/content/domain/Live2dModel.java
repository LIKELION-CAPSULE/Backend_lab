package com.capsule.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "live2d_models")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Live2dModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storagePrefix;
    private String modelJsonPath;
    private BigDecimal scale;

    @Column(name = "offset_x") // 끝 글자가 대문자면 이름 규칙이 _를 안 넣음
    private int offsetX;

    @Column(name = "offset_y")
    private int offsetY;
}
