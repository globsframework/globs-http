package org.globsframework.http;

import org.globsframework.core.model.Glob;

import java.io.InputStream;

public sealed interface HttpOutputData permits HttpOutputData.GlobHttpOutputData, HttpOutputData.KnownSizeStreamHttpOutputData {

    record SizedStream(InputStream stream, long size) {}

    static HttpOutputData asGlob(Glob glob) {
        return new GlobHttpOutputData(glob);
    }

    static HttpOutputData asStream(InputStream data, long size) {
        return new KnownSizeStreamHttpOutputData(data, size);
    }

    final class GlobHttpOutputData implements HttpOutputData {
        private final Glob glob;

        public GlobHttpOutputData(Glob glob) {
            this.glob = glob;
        }

        public Glob getGlob() {
            return glob;
        }
    }

    final class KnownSizeStreamHttpOutputData implements HttpOutputData {
        private final InputStream data;
        private final long size;

        public KnownSizeStreamHttpOutputData(InputStream data, long size) {
            this.data = data;
            this.size = size;
        }

        public SizedStream getStream() {
            return new SizedStream(data, size);
        }
    }
}
