package org.globsframework.remote.rpc.impl;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoader;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.KeyField;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.metamodel.index.MultiFieldNotUniqueIndex;
import org.globsframework.remote.shared.SharedId_;

public class ServiceType {
    public static GlobType TYPE;

    @SharedId_
    @KeyField
    public static IntegerField SHARED_ID;

    @KeyField
    public static StringField KEY;

    @KeyField
    public static StringField CLASS_NAME;

    public static StringField URL;

    public static MultiFieldNotUniqueIndex SERVICE_INDEX;

    static {
        GlobTypeLoader loader = GlobTypeLoaderFactory.create(ServiceType.class, "Service").load();
        loader.defineMultiFieldNotUniqueIndex(SERVICE_INDEX, CLASS_NAME, KEY);
    }
}
