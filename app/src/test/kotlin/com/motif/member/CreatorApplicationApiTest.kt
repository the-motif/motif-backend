package com.motif.member

import com.motif.member.domain.Member
import com.motif.member.domain.MemberRole
import com.motif.member.repository.CreatorProfileRepository
import com.motif.member.repository.MemberRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreatorApplicationApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val profileRepository: CreatorProfileRepository,
    private val em: EntityManager,
    private val jsonMapper: JsonMapper,
) {

    private var readerId = 0L
    private val adminId = 999_999L

    @BeforeEach
    fun setUp() {
        readerId = memberRepository.save(Member("apply@motif.test", "hashed", "applicant")).id!!
    }

    @Test
    fun `신청 → 201, 심사 중에 또 신청 → 409 MEMBER_008`() {
        apply(readerId).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("REQUESTED") }
        }
        apply(readerId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_008") }
        }
    }

    @Test
    fun `승인 → 회원 CREATOR + 창작자 프로필 생성, 다시 승인 → 409 MEMBER_010`() {
        val applicationId = applyAndGetId(readerId)

        adminPost("/api/admin/creator-applications/$applicationId/approve").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }
        em.flush()
        em.clear()
        assertThat(memberRepository.findById(readerId).get().role).isEqualTo(MemberRole.CREATOR)
        assertThat(profileRepository.findByMemberId(readerId)).isNotNull()

        adminPost("/api/admin/creator-applications/$applicationId/approve").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_010") }
        }
    }

    @Test
    fun `반려 → 사유 저장, 다시 신청 가능`() {
        val applicationId = applyAndGetId(readerId)

        adminPost("/api/admin/creator-applications/$applicationId/reject", """{"reason":"소개 부족"}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.rejectReason") { value("소개 부족") }
        }
        apply(readerId).andExpect { status { isCreated() } }

        mockMvc.get("/api/creator-applications/me") { with(reader(readerId)) }.andExpect {
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].status") { value("REQUESTED") }
        }
    }

    @Test
    fun `창작자가 신청 → 409 MEMBER_007`() {
        val creator = Member("creator@motif.test", "hashed", "creator").apply { promoteToCreator() }
        val creatorId = memberRepository.save(creator).id!!

        apply(creatorId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MEMBER_007") }
        }
    }

    @Test
    fun `일반 회원이 심사 API → 403`() {
        mockMvc.get("/api/admin/creator-applications") { with(reader(readerId)) }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `관리자 목록 조회 → 심사 중 신청만`() {
        applyAndGetId(readerId)

        mockMvc.get("/api/admin/creator-applications") { with(admin()) }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.memberId == $readerId)]") { exists() }
        }
    }

    private fun apply(memberId: Long) = mockMvc.post("/api/creator-applications") {
        with(reader(memberId))
        contentType = MediaType.APPLICATION_JSON
        content = """{"intro":"여행 에세이 연재","portfolioUrl":"https://blog.example.com"}"""
    }

    private fun applyAndGetId(memberId: Long): Long {
        val body = apply(memberId).andReturn().response.contentAsString
        return (jsonMapper.readValue(body, Map::class.java)["id"] as Number).toLong()
    }

    private fun adminPost(url: String, body: String? = null) = mockMvc.post(url) {
        with(admin())
        if (body != null) {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
    }

    // 토큰 발급 없이 검증 통과한 JWT로 요청
    private fun reader(memberId: Long) =
        jwt().jwt { it.subject(memberId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_READER"))

    private fun admin() =
        jwt().jwt { it.subject(adminId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
}
