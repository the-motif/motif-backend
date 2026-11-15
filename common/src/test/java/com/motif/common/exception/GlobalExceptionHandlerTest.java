package com.motif.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_코드로_응답() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_001"))
                .andExpect(jsonPath("$.message").value("이미 처리된 요청"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void 상황별_메시지를_주면_기본_메시지_대신_응답() throws Exception {
        mockMvc.perform(get("/test/business-custom"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_001"))
                .andExpect(jsonPath("$.message").value("주문 123은 이미 결제됨"));
    }

    @Test
    void 요청_본문_검증_실패는_필드별_사유와_400() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void 파라미터_제약_검증_실패는_400() throws Exception {
        mockMvc.perform(get("/test/param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void 깨진_JSON은_400() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void 지원하지_않는_메서드는_405() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }

    @Test
    void 예상_못_한_예외는_내부_메시지_숨기고_500() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_999"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다"));
    }

    enum TestErrorCode implements ErrorCode {
        DUPLICATED;

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String getCode() {
            return "TEST_001";
        }

        @Override
        public String getMessage() {
            return "이미 처리된 요청";
        }
    }

    record TestRequest(@NotBlank String name, @Min(1) long amount) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(TestErrorCode.DUPLICATED);
        }

        @GetMapping("/test/business-custom")
        void businessCustom() {
            throw new BusinessException(TestErrorCode.DUPLICATED, "주문 123은 이미 결제됨");
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/param")
        void param(@RequestParam @Min(1) int page) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("DB password=secret");
        }
    }
}
