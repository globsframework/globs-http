package org.globsframework.remote.shared.impl;

import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.GlobTypeLoader;
import org.globsframework.metamodel.GlobTypeLoaderFactory;
import org.globsframework.metamodel.annotations.KeyField;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.metamodel.fields.StringField;
import org.globsframework.metamodel.index.UniqueIndex;
import org.globsframework.remote.shared.SharedId_;

public class ShareDataManagerType {
    public static GlobType TYPE;

    @SharedId_
    @KeyField
    public static IntegerField SHARED_ID;

    @KeyField
    public static StringField PATH;

    public static IntegerField PATH_ELEMENT_COUNT;

    public static StringField HOST;

    public static IntegerField PORT;

    public static UniqueIndex PATH_INDEX;


    static {
        GlobTypeLoader loader = GlobTypeLoaderFactory.create(ShareDataManagerType.class, "ServiceManager").load();
        loader.defineUniqueIndex(PATH_INDEX, PATH);
    }

}
