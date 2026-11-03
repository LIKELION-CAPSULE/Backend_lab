package com.capsule.content.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "live2d_reaction_maps")
public class ReactionMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long modelId;
    String triggerType;
    Short intensity;

    @ManyToOne
    @JoinColumn(name = "motion_id")
    Live2dMotion motion;

    @ManyToOne
    @JoinColumn(name = "expression_id")
    Live2dExpression expression;

    public Live2dMotion motion() {
        return motion;
    }

    public Live2dExpression expression() {
        return expression;
    }
}
