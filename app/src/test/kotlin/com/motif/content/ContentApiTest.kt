package com.motif.content

import com.motif.content.api.ContentPurchaseApi
import com.motif.content.domain.AccessSource
import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
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
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val contentPurchaseApi: ContentPurchaseApi,
    private val jsonMapper: JsonMapper,
) {

    private var creatorId = 0L
    private var readerId = 0L
    private var categoryId = 0L

    @BeforeEach
    fun setUp() {
        creatorId = memberRepository.save(Member("writer@motif.test", "hashed", "작가")
            .apply { promoteToCreator() }).id!!
        readerId = memberRepository.save(Member("buyer@motif.test", "hashed", "독자")).id!!
        categoryId = createCategory("테스트카테고리").let { id(it) }
    }

    @Test
    fun `카테고리 - 비로그인 목록 조회, 중복 추가 409`() {
        mockMvc.get("/api/categories").andExpect {
            status { isOk() }
            jsonPath("$[?(@.name == '테스트카테고리')]") { exists() }
        }
        mockMvc.post("/api/admin/categories") {
            with(admin())
            json("""{"name":"테스트카테고리","sortOrder":0}""")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONTENT_007") }
        }
    }

    @Test
    fun `독자가 작성 → 403, 가격 범위 밖 → 400 CONTENT_004, 멤버십 → 400 CONTENT_005`() {
        mockMvc.post("/api/creator/contents") {
            with(member(readerId, "READER"))
            json(contentJson(price = 1000))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COMMON_005") }
        }
        createContent(contentJson(price = 50)).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CONTENT_004") }
        }
        createContent(contentJson(accessType = "MEMBERSHIP")).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CONTENT_005") }
        }
    }

    @Test
    fun `임시저장 글은 남에게 404, 발행하면 목록에 노출, 다시 발행 409`() {
        val contentId = id(createContent(contentJson(price = 1000)).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("DRAFT") }
            jsonPath("$.creatorNickname") { value("작가") }
        })

        mockMvc.get("/api/contents/$contentId").andExpect { status { isNotFound() } }

        publish(contentId).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PUBLISHED") }
        }
        mockMvc.get("/api/contents?categoryId=$categoryId").andExpect {
            status { isOk() }
            jsonPath("$.content[0].id") { value(contentId) }
            jsonPath("$.content[0].creatorNickname") { value("작가") }
            jsonPath("$.hasNext") { value(false) }
        }
        publish(contentId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONTENT_006") }
        }
    }

    @Test
    fun `유료 글 - 비구매자는 본문 없음, 권한 받으면 본문 + 첫 열람 기록, 회수하면 다시 없음`() {
        val contentId = publishedPaidContent()

        mockMvc.get("/api/contents/$contentId").andExpect {
            jsonPath("$.accessible") { value(false) }
            jsonPath("$.body") { doesNotExist() }
        }

        contentPurchaseApi.grantAccess(readerId, contentId, 1L, AccessSource.PURCHASE)
        assertThat(contentPurchaseApi.isViewed(readerId, contentId)).isFalse()

        mockMvc.get("/api/contents/$contentId") { with(member(readerId, "READER")) }.andExpect {
            jsonPath("$.accessible") { value(true) }
            jsonPath("$.body") { value("구매자만 보는 본문") }
        }
        assertThat(contentPurchaseApi.isViewed(readerId, contentId)).isTrue()

        mockMvc.get("/api/library") { with(member(readerId, "READER")) }.andExpect {
            jsonPath("$.content[0].id") { value(contentId) }
        }

        contentPurchaseApi.revokeAccess(readerId, contentId)
        mockMvc.get("/api/contents/$contentId") { with(member(readerId, "READER")) }.andExpect {
            jsonPath("$.accessible") { value(false) }
        }
    }

    @Test
    fun `환불 후 재구매 → 같은 행 갱신, 이미 권한 있으면 409 CONTENT_009`() {
        val contentId = publishedPaidContent()

        contentPurchaseApi.grantAccess(readerId, contentId, 1L, AccessSource.PURCHASE)
        contentPurchaseApi.revokeAccess(readerId, contentId)
        contentPurchaseApi.grantAccess(readerId, contentId, 2L, AccessSource.PURCHASE)

        assertThat(contentPurchaseApi.hasAccess(readerId, contentId)).isTrue()
        assertThat(contentPurchaseApi.isViewed(readerId, contentId)).isFalse()
        org.junit.jupiter.api.assertThrows<com.motif.common.exception.BusinessException> {
            contentPurchaseApi.grantAccess(readerId, contentId, 3L, AccessSource.PURCHASE)
        }.also { assertThat(it.errorCode.code).isEqualTo("CONTENT_009") }
    }

    @Test
    fun `남의 글 수정 → 403 CONTENT_003`() {
        val contentId = id(createContent(contentJson(price = 1000)))
        val otherCreatorId = memberRepository.save(Member("other@motif.test", "hashed", "다른작가")
            .apply { promoteToCreator() }).id!!

        mockMvc.put("/api/creator/contents/$contentId") {
            with(member(otherCreatorId, "CREATOR"))
            json(contentJson(price = 2000))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("CONTENT_003") }
        }
    }

    private fun publishedPaidContent(): Long {
        val contentId = id(createContent(contentJson(price = 1000)))
        publish(contentId)
        return contentId
    }

    private fun createCategory(name: String) = mockMvc.post("/api/admin/categories") {
        with(admin())
        json("""{"name":"$name","sortOrder":0}""")
    }

    private fun createContent(body: String) = mockMvc.post("/api/creator/contents") {
        with(member(creatorId, "CREATOR"))
        json(body)
    }

    private fun publish(contentId: Long) = mockMvc.post("/api/creator/contents/$contentId/publish") {
        with(member(creatorId, "CREATOR"))
    }

    private fun contentJson(price: Long = 1000, accessType: String = "PAID") = """
        {"categoryId":$categoryId,"title":"제주 기록","preview":"앞부분","accessType":"$accessType",
         "price":$price,"body":"구매자만 보는 본문"}
    """.trimIndent()

    private fun id(result: org.springframework.test.web.servlet.ResultActionsDsl): Long {
        val body = result.andReturn().response.contentAsString
        return (jsonMapper.readValue(body, Map::class.java)["id"] as Number).toLong()
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun member(memberId: Long, role: String) =
        jwt().jwt { it.subject(memberId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_$role"))

    private fun admin() = member(999_999L, "ADMIN")
}
