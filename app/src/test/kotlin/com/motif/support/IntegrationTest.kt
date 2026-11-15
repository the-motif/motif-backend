package com.motif.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.test.context.ActiveProfiles

// 통합 테스트 공통 / test 프로필 + MySQL·Redis 컨테이너 → 로컬 DB·application-local.yaml 없이 실행
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@ImportTestcontainers(SharedContainers::class)
annotation class IntegrationTest
