package org.globsframework.http;

public class HttpException extends RuntimeException {
    protected final int code;
    private final String originalMessage;

    public HttpException(int code, String originalMessage) {
        super();
        this.code = code;
        this.originalMessage = originalMessage;
    }

    public int getCode() {
        return code;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public String getMessage() {
        return code + " : " + getOriginalMessage();
    }
}
