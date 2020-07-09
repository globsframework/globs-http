package org.globsframework.http;

import org.globsframework.model.Glob;

public class HttpExceptionWithContent extends RuntimeException {
    final int code;
    final Glob message;

    public HttpExceptionWithContent(int code, Glob message) {
        this.code = code;
        this.message = message;
    }
}
