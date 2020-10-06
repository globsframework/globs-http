package org.globsframework.http;

import org.apache.http.client.methods.HttpPost;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.fields.BooleanField;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringField;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlobHttpUtilsTest {

    @Test
    public void name() {
        HttpPost path = GlobHttpUtils.createPost("path", PARAM.TYPE.instantiate().set(PARAM.aLong, 23)
                .set(PARAM.bool, true).set(PARAM.str, "Some String with &=" +
                        "sdfsfd"));
        String s = path.getURI().toString();
        Assert.assertEquals("path?str=Some+String+with+%26%3Dsdfsfd&bool=true&aLong=23", s);
    }

    static public class PARAM{
        public static GlobType TYPE;

        public static StringField str;

        public static BooleanField bool;

        public static LongField aLong;

        static {
            GlobTypeLoaderFactory.create(PARAM.class).load();
        }
    }
}