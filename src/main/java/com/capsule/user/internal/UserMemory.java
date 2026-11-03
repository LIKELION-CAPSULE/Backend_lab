package com.capsule.user.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/** user_memories 테이블. 날짜·시간은 타임존 없이 한국 날짜·시간 그대로. created_at은 DB 기본값 */
@Entity
@Table(name = "user_memories")
class UserMemory {

    enum Type {
        EXAM,
        GOAL,
        PROMISE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;

    @Enumerated(EnumType.STRING)
    private Type type;

    private String title;
    private LocalDate dueDate;
    private LocalTime dueTime;

    protected UserMemory() {}

    UserMemory(long userId, Type type, String title, LocalDate dueDate, LocalTime dueTime) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
    }

    long id() {
        return id;
    }

    Type type() {
        return type;
    }

    String title() {
        return title;
    }

    LocalDate dueDate() {
        return dueDate;
    }

    LocalTime dueTime() {
        return dueTime;
    }
}
