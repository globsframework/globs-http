package org.globsframework.http;

import org.globsframework.core.model.Glob;

import java.io.InputStream;

public interface HttpInputData {
    InputStream asStream();

    Glob asGlob();

    boolean isGlob();

    static HttpInputData fromGlob(Glob glob) {
        return new HttpInputData() {
            @Override
            public InputStream asStream() {
                return null;
            }

            @Override
            public Glob asGlob() {
                return glob;
            }

            @Override
            public boolean isGlob() {
                return true;
            }
        };
    }

    static HttpInputData fromStream(InputStream inputStream) {
        return new HttpInputData() {
            @Override
            public InputStream asStream() {
                return inputStream;
            }

            @Override
            public Glob asGlob() {
                return null;
            }

            @Override
            public boolean isGlob() {
                return false;
            }
        };
    }
}
