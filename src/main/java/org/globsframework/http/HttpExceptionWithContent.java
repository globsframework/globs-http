package org.globsframework.http;

import org.globsframework.model.Glob;

public class HttpExceptionWithContent extends RuntimeException {
    final int code;
    final Glob content;

    public HttpExceptionWithContent(int code, Glob content) {
        super();
        this.code = code;
        this.content = content;
    }

    public int getCode() {
        return code;
    }

    public Glob getContent() {
        return content;
    }

    public String getMessage() {
        return code + " : " + content.toString();
    }
}
