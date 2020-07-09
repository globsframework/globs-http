package org.globsframework.http;

public class HttpException extends RuntimeException {
    final int code;
    final String message;

    public HttpException(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
