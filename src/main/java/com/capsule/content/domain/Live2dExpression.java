package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live2d_expressions")
public class Live2dExpression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String name;
    String filePath;

    public String name() {
        return name;
    }

    public String filePath() {
        return filePath;
    }
}
