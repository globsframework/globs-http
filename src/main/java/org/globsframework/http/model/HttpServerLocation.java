package org.globsframework.http.model;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.KeyField;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.remote.shared.SharedId_;

public class HttpServerLocation {
    public static GlobType TYPE;

    @SharedId_
    @KeyField
    public static LongField SHARED_ID;

    @KeyField
    public static StringField SERVICE;

    @KeyField
    public static StringField NAME;

    public static StringField HOST;

    public static StringField PORT;

    static {
        GlobTypeLoaderFactory.create(HttpServerLocation.class).load();
    }
}
