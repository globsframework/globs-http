package org.globsframework.http;

public class HttpException extends RuntimeException {
    final int code;
    final String message;

    public HttpException(int code, String message) {
        super();
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getOriginalMessage() {
        return message;
    }

    public String getMessage() {
        return code + " : " + message;
    }
}
