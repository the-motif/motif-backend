package com.motif.member

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

// 테스트마다 롤백
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `회원가입 성공 → 201, 비밀번호는 응답에 없음`() {
        signup("""{"email":"New@Motif.test","password":"password123","nickname":"새독자"}""").andExpect {
            status { isCreated() }
            jsonPath("$.id") { isNumber() }
            jsonPath("$.email") { value("new@motif.test") }
            jsonPath("$.role") { value("READER") }
            jsonPath("$.password") { doesNotExist() }
        }
    }

    @Test
    fun `같은 이메일로 다시 가입 → 409 MEMBER_001`() {
        signup("""{"email":"dup@motif.test","password":"password123","nickname":"first"}""")
        signup("""{"email":"dup@motif.test","password":"password123","nickname":"second"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_001") }
        }
    }

    @Test
    fun `같은 닉네임 → 409 MEMBER_002`() {
        signup("""{"email":"a@motif.test","password":"password123","nickname":"same"}""")
        signup("""{"email":"b@motif.test","password":"password123","nickname":"same"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_002") }
        }
    }

    @Test
    fun `잘못된 입력 → 400, 필드별 사유`() {
        signup("""{"email":"not-email","password":"short"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_001") }
            jsonPath("$.errors.length()") { value(3) }
        }
    }

    private fun signup(body: String) = mockMvc.post("/api/members") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }
}
