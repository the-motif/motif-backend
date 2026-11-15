package com.motif.common.trace;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

// MDC 키 traceId / 요청은 TraceIdFilter, 배치·스케줄러는 run()으로
@NullMarked
public final class TraceId {

    public static final String MDC_KEY = "traceId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private TraceId() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // 외부 값은 형식 검사 (로그 주입 방지)
    public static String acceptOrNew(@Nullable String candidate) {
        return candidate != null && VALID.matcher(candidate).matches() ? candidate : newId();
    }

    public static @Nullable String current() {
        return MDC.get(MDC_KEY);
    }

    // 이전 값 복원 → 중첩 호출에도 안전
    public static Scope open(String traceId) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        return new Scope(previous);
    }

    // 스케줄러용 / 예: TraceId.run("settle", () -> job.run(date))
    public static <T> T run(String prefix, Supplier<T> work) {
        try (Scope ignored = open(prefix + "-" + newId())) {
            return work.get();
        }
    }

    public static void run(String prefix, Runnable work) {
        try (Scope ignored = open(prefix + "-" + newId())) {
            work.run();
        }
    }

    public static final class Scope implements AutoCloseable {

        private final @Nullable String previous;

        private Scope(@Nullable String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
