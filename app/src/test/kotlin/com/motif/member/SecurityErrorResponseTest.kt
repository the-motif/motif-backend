package com.motif.member

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorResponseTest @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `로그인 없이 관리자 경로 → 401 공통 에러 응답`() {
        mockMvc.get("/api/admin/anything").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("COMMON_004") }
            jsonPath("$.errors") { isEmpty() }
        }
    }

    @Test
    @WithMockUser(roles = ["READER"])
    fun `일반 회원이 관리자 경로 → 403 공통 에러 응답`() {
        mockMvc.get("/api/admin/anything").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COMMON_005") }
        }
    }

    @Test
    @WithMockUser
    fun `로그인한 상태로 없는 URL → 404`() {
        mockMvc.get("/api/no-such-path").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMON_002") }
        }
    }

    @Test
    fun `로그인 없이 없는 URL → 401`() {
        mockMvc.get("/api/no-such-path").andExpect {
            status { isUnauthorized() }
        }
    }
}
