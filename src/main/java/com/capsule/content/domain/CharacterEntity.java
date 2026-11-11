package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 이름이 Character면 java.lang.Character와 겹쳐서 Entity를 붙임 */
@Entity
@Table(name = "characters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long roomId;

    @ManyToOne
    @JoinColumn(name = "model_id")
    private Live2dModel model;

    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> personality;

    private String intro;
    private String profileImageUrl;
    private String homeImageUrl;
    private int sortOrder;
    private boolean isActive;
}
