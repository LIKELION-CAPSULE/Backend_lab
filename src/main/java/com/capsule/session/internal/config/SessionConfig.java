package com.capsule.session.internal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 자동 마감 배치({@link com.capsule.session.internal.service.SessionService#autoCloseStale()})용 */
@Configuration
@EnableScheduling
class SessionConfig {}
