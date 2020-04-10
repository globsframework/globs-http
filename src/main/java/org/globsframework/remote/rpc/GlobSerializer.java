package org.globsframework.remote.rpc;

import org.globsframework.directory.Directory;
import org.globsframework.metamodel.GlobModel;
import org.globsframework.model.Glob;
import org.globsframework.remote.Serializer;
import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedOutput;

public class GlobSerializer implements Serializer<Glob> {
    private GlobModel globModel;

    public GlobSerializer() {
    }

    public GlobSerializer(GlobModel globModel) {
        this.globModel = globModel;
    }

    public Class getClassType() {
        return Glob.class;
    }

    public Glob read(SerializedInput serializedInput, Directory directory) {
        return serializedInput.readGlob(globModel);
    }

    public void write(Glob object, SerializedOutput serializedOutput) {
        serializedOutput.writeGlob(object);
    }

    public void setGlobModel(GlobModel globModel) {
        this.globModel = globModel;
    }
}
