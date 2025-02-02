package org.globsframework.http;

import org.globsframework.core.model.Glob;

import java.io.InputStream;

public interface HttpInputData {

    record SizedStream(InputStream stream, long size) {}

    SizedStream asStream();

    Glob asGlob();

    boolean isGlob();

    static HttpInputData fromGlob(Glob glob) {
        return new HttpInputData() {
            @Override
            public SizedStream asStream() {
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

    static HttpInputData fromStream(InputStream inputStream, long size) {
        return new HttpInputData() {
            @Override
            public SizedStream asStream() {
                return new SizedStream(inputStream, size);
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
