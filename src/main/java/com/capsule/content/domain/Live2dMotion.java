package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live2d_motions")
public class Live2dMotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String groupName;
    String filePath;

    public String groupName() {
        return groupName;
    }

    public String filePath() {
        return filePath;
    }
}
