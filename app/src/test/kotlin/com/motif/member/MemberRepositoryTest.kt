package com.motif.member

import com.motif.common.config.ClockConfig
import com.motif.common.config.JpaAuditingConfig
import com.motif.member.domain.Member
import com.motif.member.domain.MemberRole
import com.motif.member.domain.MemberStatus
import com.motif.member.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import

// 로컬 MySQL(Flyway 적용) 기준 / 테스트 후 롤백
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ClockConfig::class, JpaAuditingConfig::class)
class MemberRepositoryTest @Autowired constructor(
    private val memberRepository: MemberRepository,
    private val em: EntityManager,
) {

    @Test
    fun `저장하면 기본 역할·상태와 생성 시각이 채워진다`() {
        memberRepository.save(Member("reader@motif.test", "hashed", "reader"))
        em.flush()
        em.clear()

        val found = memberRepository.findByEmail("reader@motif.test")!!

        assertThat(found.id).isNotNull()
        assertThat(found.role).isEqualTo(MemberRole.READER)
        assertThat(found.status).isEqualTo(MemberStatus.ACTIVE)
        assertThat(found.createdAt).isNotNull()
        assertThat(found.updatedAt).isNotNull()
    }

    @Test
    fun `이메일·닉네임 존재 여부를 확인한다`() {
        memberRepository.save(Member("dup@motif.test", "hashed", "dup"))

        assertThat(memberRepository.existsByEmail("dup@motif.test")).isTrue()
        assertThat(memberRepository.existsByNickname("dup")).isTrue()
        assertThat(memberRepository.existsByEmail("none@motif.test")).isFalse()
    }
}
