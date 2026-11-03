package com.capsule.user.internal.domain;

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
public class UserMemory {

    public enum Type {
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

    public UserMemory(long userId, Type type, String title, LocalDate dueDate, LocalTime dueTime) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
    }

    public long id() {
        return id;
    }

    public Type type() {
        return type;
    }

    public String title() {
        return title;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public LocalTime dueTime() {
        return dueTime;
    }
}
