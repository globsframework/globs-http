package org.globsframework.http;

import org.globsframework.model.Glob;

import java.io.InputStream;

public interface HttpOutputData {

    boolean isGlob();

    Glob getGlob();

    InputStream getStream();


    static HttpOutputData asGlob(Glob glob) {
        return new HttpOutputData() {
            @Override
            public boolean isGlob() {
                return true;
            }

            @Override
            public Glob getGlob() {
                return glob;
            }

            @Override
            public InputStream getStream() {
                return null;
            }
        };
    }

    static HttpOutputData asStream(InputStream data) {
        return new HttpOutputData() {
            @Override
            public boolean isGlob() {
                return false;
            }

            @Override
            public Glob getGlob() {
                return null;
            }

            @Override
            public InputStream getStream() {
                return data;
            }
        };
    }
}
