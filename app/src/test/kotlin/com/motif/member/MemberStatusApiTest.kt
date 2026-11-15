package com.motif.member

import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

// 차단 검증은 실제 JwtDecoder를 거쳐야 해서 로그인으로 받은 토큰 사용
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberStatusApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jsonMapper: JsonMapper,
) {

    private var memberId = 0L

    @BeforeEach
    fun setUp() {
        memberId = memberRepository
            .save(Member("status@motif.test", passwordEncoder.encode("password123")!!, "statususer"))
            .id!!
    }

    @Test
    fun `탈퇴 → 기존 토큰·리프레시 무효, 다시 로그인 불가`() {
        val tokens = login()

        withdraw(tokens.access, "wrong123").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("MEMBER_011") }
        }
        withdraw(tokens.access, "password123").andExpect { status { isNoContent() } }

        me(tokens.access).andExpect { status { isUnauthorized() } }
        refresh(tokens.refresh).andExpect { status { isUnauthorized() } }
        loginRequest().andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MEMBER_004") }
        }
    }

    @Test
    fun `관리자 정지 → 쓰던 액세스 토큰 즉시 401, 해제 후 다시 로그인 가능`() {
        val tokens = login()
        me(tokens.access).andExpect { status { isOk() } }

        adminPost("/api/admin/members/$memberId/suspend", """{"reason":"약관 위반"}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUSPENDED") }
        }
        me(tokens.access).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("COMMON_004") }
        }
        refresh(tokens.refresh).andExpect { status { isUnauthorized() } }
        loginRequest().andExpect { status { isForbidden() } }

        adminPost("/api/admin/members/$memberId/unsuspend").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
        }
        me(login().access).andExpect { status { isOk() } }
    }

    @Test
    fun `정지 중 다시 정지 → 409 MEMBER_012`() {
        adminPost("/api/admin/members/$memberId/suspend", """{"reason":"1차"}""")
        adminPost("/api/admin/members/$memberId/suspend", """{"reason":"2차"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_012") }
        }
    }

    private data class Tokens(val access: String, val refresh: String)

    private fun loginRequest() = mockMvc.post("/api/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"status@motif.test","password":"password123"}"""
    }

    private fun login(): Tokens {
        val body = loginRequest().andExpect { status { isOk() } }.andReturn().response.contentAsString
        val map = jsonMapper.readValue(body, Map::class.java)
        return Tokens(map["accessToken"] as String, map["refreshToken"] as String)
    }

    private fun me(accessToken: String) = mockMvc.get("/api/members/me") {
        header("Authorization", "Bearer $accessToken")
    }

    private fun withdraw(accessToken: String, password: String) = mockMvc.post("/api/members/me/withdraw") {
        header("Authorization", "Bearer $accessToken")
        contentType = MediaType.APPLICATION_JSON
        content = """{"password":"$password"}"""
    }

    private fun refresh(refreshToken: String) = mockMvc.post("/api/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"refreshToken":"$refreshToken"}"""
    }

    private fun adminPost(url: String, body: String? = null) = mockMvc.post(url) {
        with(jwt().jwt { it.subject("999999") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
    }
}
