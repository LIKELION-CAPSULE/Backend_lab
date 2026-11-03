package com.capsule.content.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "live2d_motions")
class Live2dMotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String groupName;
    String filePath;
}
