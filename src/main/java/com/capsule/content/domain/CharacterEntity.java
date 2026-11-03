package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 이름이 Character면 java.lang.Character와 겹쳐서 Entity를 붙임 */
@Entity
@Table(name = "characters")
public class CharacterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long roomId;

    @ManyToOne
    @JoinColumn(name = "model_id")
    Live2dModel model;

    String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    List<String> personality;

    String intro;
    String profileImageUrl;
    int sortOrder;
    boolean isActive;

    public Long id() {
        return id;
    }

    public long roomId() {
        return roomId;
    }

    public Live2dModel model() {
        return model;
    }

    public String name() {
        return name;
    }

    public List<String> personality() {
        return personality;
    }

    public String intro() {
        return intro;
    }

    public String profileImageUrl() {
        return profileImageUrl;
    }

    public boolean isActive() {
        return isActive;
    }
}
