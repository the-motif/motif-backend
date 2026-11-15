package com.motif.member.auth

// 컨트롤러 파라미터에 로그인 회원 ID 주입 / Java·Kotlin 공용
// required = false면 비로그인 시 null
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CurrentMemberId(val required: Boolean = true)
