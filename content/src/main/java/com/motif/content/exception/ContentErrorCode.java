package com.motif.content.exception;

import com.motif.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ContentErrorCode implements ErrorCode {

    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_001", "카테고리를 찾을 수 없습니다"),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_002", "콘텐츠를 찾을 수 없습니다"),
    NOT_OWNER(HttpStatus.FORBIDDEN, "CONTENT_003", "본인 콘텐츠만 수정할 수 있습니다"),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "CONTENT_004", "유료 콘텐츠 가격은 100~1,000,000 포인트입니다"),
    MEMBERSHIP_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "CONTENT_005", "멤버십 콘텐츠는 아직 지원하지 않습니다"),
    ALREADY_PUBLISHED(HttpStatus.CONFLICT, "CONTENT_006", "이미 발행된 콘텐츠입니다"),
    DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "CONTENT_007", "이미 있는 카테고리입니다"),
    NOT_FOR_SALE(HttpStatus.CONFLICT, "CONTENT_008", "구매할 수 없는 콘텐츠입니다"),
    ALREADY_OWNED(HttpStatus.CONFLICT, "CONTENT_009", "이미 열람 권한이 있습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
