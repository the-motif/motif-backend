package com.motif.member.auth

import org.springframework.security.oauth2.jwt.Jwt

// sub = 회원 ID
fun Jwt.memberId(): Long = subject!!.toLong()
