package com.motif.member

import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jsonMapper: JsonMapper,
) {

    @BeforeEach
    fun setUp() {
        memberRepository.save(Member("auth@motif.test", passwordEncoder.encode("password123")!!, "authuser"))
    }

    @Test
    fun `로그인 성공 → 토큰으로 내 정보 조회`() {
        val tokens = login("password123")

        mockMvc.get("/api/members/me") {
            header("Authorization", "Bearer ${tokens["accessToken"]}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("auth@motif.test") }
        }
    }

    @Test
    fun `비밀번호 틀림 → 401 MEMBER_003`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"auth@motif.test","password":"wrong123"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("MEMBER_003") }
        }
    }

    @Test
    fun `토큰 없이 내 정보 → 401 COMMON_004`() {
        mockMvc.get("/api/members/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("COMMON_004") }
        }
    }

    @Test
    fun `위조 토큰 → 401 COMMON_004`() {
        mockMvc.get("/api/members/me") {
            header("Authorization", "Bearer abc.def.ghi")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("COMMON_004") }
        }
    }

    @Test
    fun `리프레시 토큰은 한 번만 사용 가능`() {
        val refreshToken = login("password123")["refreshToken"]

        refresh(refreshToken).andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isNotEmpty() }
        }
        refresh(refreshToken).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("MEMBER_005") }
        }
    }

    @Test
    fun `로그아웃한 리프레시 토큰 → 재발급 불가`() {
        val refreshToken = login("password123")["refreshToken"]

        mockMvc.post("/api/auth/logout") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refreshToken"}"""
        }.andExpect { status { isNoContent() } }

        refresh(refreshToken).andExpect { status { isUnauthorized() } }
    }

    private fun login(password: String): Map<*, *> {
        val body = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"auth@motif.test","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return jsonMapper.readValue(body, Map::class.java)
    }

    private fun refresh(refreshToken: Any?) = mockMvc.post("/api/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"refreshToken":"$refreshToken"}"""
    }
}
