package com.capsule.session.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 자동 마감 배치({@link SessionService#autoCloseStale()})용 */
@Configuration
@EnableScheduling
class SessionConfig {}
