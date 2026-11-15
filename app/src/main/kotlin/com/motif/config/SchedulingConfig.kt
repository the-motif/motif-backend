package com.motif.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

// outbox 발행, 일일 정산
@Configuration
@EnableScheduling
class SchedulingConfig
