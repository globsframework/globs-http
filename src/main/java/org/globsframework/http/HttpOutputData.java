package org.globsframework.http;

import org.globsframework.core.model.Glob;

import java.io.InputStream;

public interface HttpOutputData {

    boolean isGlob();

    Glob getGlob();

    InputStream getStream();


    static HttpOutputData asGlob(Glob glob) {
        return new HttpOutputData() {
            public boolean isGlob() {
                return true;
            }

            public Glob getGlob() {
                return glob;
            }

            public InputStream getStream() {
                return null;
            }
        };
    }

    static HttpOutputData asStream(InputStream data) {
        return new HttpOutputData() {
            public boolean isGlob() {
                return false;
            }

            public Glob getGlob() {
                return null;
            }

            public InputStream getStream() {
                return data;
            }
        };
    }
}
