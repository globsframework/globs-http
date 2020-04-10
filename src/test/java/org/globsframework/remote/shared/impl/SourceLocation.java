package org.globsframework.remote.shared.impl;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.DefaultInteger;
import org.globsframework.metamodel.annotations.DefaultLong;
import org.globsframework.metamodel.annotations.KeyField;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.LongField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.remote.shared.SharedId_;

public class SourceLocation {
    public static GlobType TYPE;

    @SharedId_
    @KeyField
    public static IntegerField SHARED_ID;

    @KeyField
    public static IntegerField ID;

    public static StringField SOURCE_NAME;

    public static StringField URL;

    @DefaultInteger(0)
    public static IntegerField COUNT;

    @DefaultLong(0)
    public static LongField LAST_UPDATE_TIME;


    static {
        GlobTypeLoaderFactory.create(SourceLocation.class).load();
    }

}
