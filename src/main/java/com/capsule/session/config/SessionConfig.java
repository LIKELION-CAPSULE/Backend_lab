package com.capsule.session.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 자동 마감 배치({@link com.capsule.session.service.SessionService#autoCloseStale()})용 */
@Configuration
@EnableScheduling
class SessionConfig {}
