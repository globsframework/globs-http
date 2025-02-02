package org.globsframework.http;

import org.globsframework.core.model.Glob;

import java.io.InputStream;

public interface HttpOutputData {

    boolean isGlob();

    Glob getGlob();

    record SizedStream(InputStream stream, long size) {}
    SizedStream getStream();


    static HttpOutputData asGlob(Glob glob) {
        return new HttpOutputData() {
            public boolean isGlob() {
                return true;
            }

            public Glob getGlob() {
                return glob;
            }

            public SizedStream getStream() {
                return null;
            }

        };
    }

    static HttpOutputData asStream(InputStream data, long size) {
        return new HttpOutputData() {
            public boolean isGlob() {
                return false;
            }

            public Glob getGlob() {
                return null;
            }

            public SizedStream getStream() {
                return new SizedStream(data, size);
            }

        };
    }
}
