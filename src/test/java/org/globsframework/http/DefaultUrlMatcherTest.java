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

    @Test
    public void name3() {
        DefaultUrlMatcher defaultUrlMatcher = new DefaultUrlMatcher(TEST_2.TYPE, "/XX/{A}/XXX");
        Glob url = defaultUrlMatcher.parse("/XX/AAA/XXX");
        Assert.assertEquals("AAA", url.get(TEST_2.A));
    }

    @Test
    public void name4() {
        DefaultUrlMatcher defaultUrlMatcher = new DefaultUrlMatcher(URLParameterCustomerWorkflow.TYPE, "/the-oz/glinda/1.0.0/customer/{customerId}/workflow/{workflowId}");
        Glob url = defaultUrlMatcher.parse("/the-oz/glinda/1.0.0/customer/1111111/workflow/12343212345");
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

    public static class URLParameterCustomerWorkflow {
        public static GlobType TYPE;

        //       @FieldNameAnnotation("id")
        public static StringField customerId;
        public static StringField workflowId;

        private URLParameterCustomerWorkflow(){} //Hiding default public constructor

        static {
            GlobTypeLoaderFactory.create(URLParameterCustomerWorkflow.class, true).load();
        }
    }
}
