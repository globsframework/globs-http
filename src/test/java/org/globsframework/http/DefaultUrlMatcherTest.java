package org.globsframework.http;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeLoaderFactory;
import org.globsframework.core.metamodel.fields.StringArrayField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.junit.Assert;
import org.junit.Test;

public class DefaultUrlMatcherTest {

    @Test
    public void name() {
        UrlMatcher defaultUrlMatcher = DefaultUrlMatcher.create(TEST_1.TYPE, "/{A}/{B}/XXX/{D}");
        Glob url = defaultUrlMatcher.parse("AZZ/CSA/XXX/3F".split("/"));
        Assert.assertEquals("AZZ", url.get(TEST_1.A));
        Assert.assertEquals("CSA", url.get(TEST_1.B));
        Assert.assertEquals("3F", url.get(TEST_1.D));
    }

    @Test
    public void nameAllElementInPath() {
        UrlMatcher defaultUrlMatcher = DefaultUrlMatcher.create(TEST_3.TYPE, "/{ALL}");
        Glob url = defaultUrlMatcher.parse("AZZ/CSA/XXX/3F".split("/"));
        Assert.assertEquals("AZZ", url.get(TEST_3.ALL)[0]);
        Assert.assertEquals("CSA", url.get(TEST_3.ALL)[1]);
        Assert.assertEquals("XXX", url.get(TEST_3.ALL)[2]);
        Assert.assertEquals("3F", url.get(TEST_3.ALL)[3]);
    }

    @Test
    public void nameManyElementInPath() {
        UrlMatcher defaultUrlMatcher = DefaultUrlMatcher.create(TEST_4.TYPE, "/{A}/{ALL}");
        Glob url = defaultUrlMatcher.parse("AZZ/CSA/XXX/3F".split("/"));
        Assert.assertEquals("AZZ", url.get(TEST_4.A));
        Assert.assertEquals("CSA", url.get(TEST_4.ALL)[0]);
        Assert.assertEquals("XXX", url.get(TEST_4.ALL)[1]);
        Assert.assertEquals("3F", url.get(TEST_4.ALL)[2]);
    }

    @Test
    public void name2() {
        UrlMatcher defaultUrlMatcher = DefaultUrlMatcher.create(TEST_1.TYPE, "/XX/{A}/{B}/XXX/{D}");
        Glob url = defaultUrlMatcher.parse("XX/AZZ/CSA/XXX/3F".split("/"));
        Assert.assertEquals("AZZ", url.get(TEST_1.A));
        Assert.assertEquals("CSA", url.get(TEST_1.B));
        Assert.assertEquals("3F", url.get(TEST_1.D));
    }

    @Test
    public void name3() {
        UrlMatcher defaultUrlMatcher = DefaultUrlMatcher.create(TEST_2.TYPE, "/XX/{A}/XXX");
        Glob url = defaultUrlMatcher.parse("XX/AAA/XXX".split("/"));
        Assert.assertEquals("AAA", url.get(TEST_2.A));
    }

    @Test
    public void name4() {
        UrlMatcher defaultUrlMatcher = DefaultUrlMatcher.create(URLParameterCustomerWorkflow.TYPE, "/the-oz/glinda/1.0.0/customer/{customerId}/workflow/{workflowId}");
        Glob url = defaultUrlMatcher.parse("the-oz/glinda/1.0.0/customer/1111111/workflow/12343212345".split("/"));
        Assert.assertEquals("1111111", url.get(URLParameterCustomerWorkflow.customerId));
        Assert.assertEquals("12343212345", url.get(URLParameterCustomerWorkflow.workflowId));
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

    public static class TEST_2 {
        public static GlobType TYPE;

        public static StringField A;

        static {
            GlobTypeLoaderFactory.create(TEST_2.class).load();
        }
    }

    public static class TEST_3 {
        public static GlobType TYPE;

        public static StringArrayField ALL;

        static {
            GlobTypeLoaderFactory.create(TEST_3.class).load();
        }
    }

    public static class TEST_4 {
        public static GlobType TYPE;

        public static StringField A;

        public static StringArrayField ALL;

        static {
            GlobTypeLoaderFactory.create(TEST_4.class).load();
        }
    }


    public static class URLParameterCustomerWorkflow {
        public static GlobType TYPE;

        //       @FieldNameAnnotation("id")
        public static StringField customerId;
        public static StringField workflowId;

        private URLParameterCustomerWorkflow() {
        } //Hiding default public constructor

        static {
            GlobTypeLoaderFactory.create(URLParameterCustomerWorkflow.class, true).load();
        }
    }
}
