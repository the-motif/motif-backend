package com.motif.member

import com.motif.member.config.AdminAccountInitializer
import com.motif.member.domain.MemberRole
import com.motif.member.repository.MemberRepository
import com.motif.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

// 기동 시 생성되므로 트랜잭션 롤백 대상 아님 / 이메일은 이 테스트 전용
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["motif.admin.email=First-Admin@motif.test", "motif.admin.password=admin1234"])
class AdminAccountInitializerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val initializer: AdminAccountInitializer,
    private val jsonMapper: JsonMapper,
) {

    @Test
    fun `기동 시 관리자 생성 → 로그인 토큰으로 관리자 API 호출`() {
        val admin = memberRepository.findByEmail("first-admin@motif.test")!!
        assertThat(admin.role).isEqualTo(MemberRole.ADMIN)

        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"first-admin@motif.test","password":"admin1234"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val token = jsonMapper.readValue(body, Map::class.java)["accessToken"]

        mockMvc.get("/api/admin/creator-applications") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `다시 실행해도 1명 (이미 있으면 건너뜀)`() {
        initializer.run(DefaultApplicationArguments())

        assertThat(memberRepository.findAll().count { it.email == "first-admin@motif.test" }).isEqualTo(1)
    }

    @Test
    fun `약한 비밀번호(ADMIN)는 기동 실패`() {
        val weak = AdminAccountInitializer(memberRepository, passwordEncoder, "weak-admin@motif.test", "ADMIN", "약한관리자")

        assertThrows<IllegalArgumentException> { weak.run(DefaultApplicationArguments()) }
        assertThat(memberRepository.existsByEmail("weak-admin@motif.test")).isFalse()
    }
}
