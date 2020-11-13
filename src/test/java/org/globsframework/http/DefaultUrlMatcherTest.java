package org.globsframework.http;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.model.Glob;
import org.junit.Assert;
import org.junit.Test;

public class DefaultUrlMatcherTest {

    @Test
    public void name() {
        DefaultUrlMatcher defaultUrlMatcher = new DefaultUrlMatcher(TEST_1.TYPE, "/{A}/{B}/XXX/{D}");
        Glob url = defaultUrlMatcher.parse("/AZZ/CSA/XXX/3F");
        Assert.assertEquals("AZZ", url.get(TEST_1.A));
        Assert.assertEquals("CSA", url.get(TEST_1.B));
        Assert.assertEquals("3F", url.get(TEST_1.D));
    }

    @Test
    public void name2() {
        DefaultUrlMatcher defaultUrlMatcher = new DefaultUrlMatcher(TEST_1.TYPE, "/XX/{A}/{B}/XXX/{D}");
        Glob url = defaultUrlMatcher.parse("/XX/AZZ/CSA/XXX/3F");
        Assert.assertEquals("AZZ", url.get(TEST_1.A));
        Assert.assertEquals("CSA", url.get(TEST_1.B));
        Assert.assertEquals("3F", url.get(TEST_1.D));
    }

    public static class TEST_1 {
        public static GlobType TYPE;

        public static StringField A;
        public static StringField B;
        public static StringField D;

        static {
            GlobTypeLoaderFactory.create(TEST_1.class).load();
        }
    }
}