package com.sentinelai.observability;

public final class RequestContext {

    public static final String HEADER = "X-Request-ID";

    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        CURRENT_REQUEST_ID.set(requestId);
    }

    public static String requestId() {
        String requestId = CURRENT_REQUEST_ID.get();
        return requestId == null || requestId.isBlank() ? "background" : requestId;
    }

    public static void clear() {
        CURRENT_REQUEST_ID.remove();
    }
}
