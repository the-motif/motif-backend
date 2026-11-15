package com.motif.member.config

import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// 첫 관리자 계정 / 기동 시 motif.admin.email이 있고 그 계정이 없으면 생성
// 값은 local yaml·운영 환경 변수로만 (Flyway·코드에 비밀번호 금지), 이미 있으면 아무것도 안 함
@Component
class AdminAccountInitializer(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${motif.admin.email:}") private val email: String,
    @Value("\${motif.admin.password:}") private val password: String,
    @Value("\${motif.admin.nickname:관리자}") private val nickname: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (email.isBlank()) return
        val normalized = email.trim().lowercase()
        if (memberRepository.existsByEmail(normalized)) return
        // 회원가입과 같은 규칙 → 약한 비밀번호면 기동 실패
        require(PASSWORD_RULE.matches(password)) { "motif.admin.password는 영문·숫자 포함 8~64자" }

        val admin = Member(normalized, passwordEncoder.encode(password)!!, nickname).apply { grantAdmin() }
        memberRepository.save(admin)
        log.info("첫 관리자 계정 생성 id={} email={}", admin.id, normalized)
    }

    companion object {
        private val PASSWORD_RULE = Regex("^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$")
    }
}
