package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "live2d_reaction_maps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReactionMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long modelId;
    private String triggerType;
    private Short intensity;

    @ManyToOne
    @JoinColumn(name = "motion_id")
    private Live2dMotion motion;

    @ManyToOne
    @JoinColumn(name = "expression_id")
    private Live2dExpression expression;
}
