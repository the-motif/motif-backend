package com.motif.common.exception;

import org.springframework.http.HttpStatus;

// 모듈별 enum으로 구현 / code는 모듈 접두어 + 번호 (예: MEMBER_001)
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
