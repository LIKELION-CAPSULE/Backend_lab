package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_backgrounds")
public class RoomBackground {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long roomId;
    String name;
    String imageUrl;
    boolean isDefault;

    public Long id() {
        return id;
    }

    public long roomId() {
        return roomId;
    }

    public String name() {
        return name;
    }

    public String imageUrl() {
        return imageUrl;
    }
}
