package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_rooms")
public class StudyRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String name;
    String description;
    String thumbnailUrl;
    String bannerUrl;
    int sortOrder;
    boolean isActive;

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String thumbnailUrl() {
        return thumbnailUrl;
    }

    /** 룸 상세(H04) 가로 배너. 없으면 null (api.md 12) */
    public String bannerUrl() {
        return bannerUrl;
    }

    public boolean isActive() {
        return isActive;
    }
}
