package com.capsule.content.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "room_backgrounds")
class RoomBackground {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long roomId;
    String name;
    String imageUrl;
    boolean isDefault;
}
