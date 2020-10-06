package org.globsframework.remote.shared.impl;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobModel;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.IntegerField;
import org.globsframework.model.Glob;
import org.globsframework.model.GlobList;
import org.globsframework.model.GlobRepository;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.utils.GlobFunctor;
import org.globsframework.model.utils.GlobMatchers;
import org.globsframework.remote.shared.SharedId;
import org.globsframework.remote.shared.SharedId_;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SharedModelType {
    private GlobModel globModel;
    private Map<GlobType, IntegerField> globTypeToConnectionField = new HashMap<GlobType, IntegerField>();

    public SharedModelType(GlobModel globModel) {
        this.globModel = globModel;
        Collection<GlobType> all = globModel.getAll();
        for (GlobType globType : all) {
            for (Field field : globType.getFields()) {
                if (field.hasAnnotation(SharedId.KEY)) {
                    globTypeToConnectionField.put(globType, field.asIntegerField());
                    break;
                }
            }
        }
    }

    public void deleteOther(GlobRepository repository, final int connectionId) {
        final GlobList toDelete = new GlobList();
        for (final Map.Entry<GlobType, IntegerField> globTypeFieldEntry : globTypeToConnectionField.entrySet()) {
            repository.safeApply(globTypeFieldEntry.getKey(), GlobMatchers.ALL, new GlobFunctor() {
                public void run(Glob glob, GlobRepository repository) throws Exception {
                    IntegerField field = globTypeFieldEntry.getValue();
                    Integer id = glob.get(field);
                    if (id != null && id != connectionId) {
                        toDelete.add(glob);
                    }
                }
            });
            repository.delete(toDelete);
            toDelete.clear();
        }
    }

    public void setOwnId(GlobRepository repository, final int connectionId) {
        for (final Map.Entry<GlobType, IntegerField> globTypeFieldEntry : globTypeToConnectionField.entrySet()) {
            repository.safeApply(globTypeFieldEntry.getKey(), GlobMatchers.ALL, new GlobFunctor() {
                public void run(Glob glob, GlobRepository repository) throws Exception {
                    IntegerField field = globTypeFieldEntry.getValue();
                    Integer id = glob.get(field);
                    if (id == null || id == 0) {
                        repository.update(glob.getKey(), field, connectionId);
                    }
                }
            });
        }
    }

    public IntegerField get(GlobType globType) {
        return globTypeToConnectionField.get(globType);
    }

    public void cleanThisId(GlobRepository globRepository, int clientId) {
        for (Map.Entry<GlobType, IntegerField> globTypeIntegerFieldEntry : globTypeToConnectionField.entrySet()) {
            GlobList all = globRepository.getAll(globTypeIntegerFieldEntry.getKey(),
                    GlobMatchers.fieldEquals(globTypeIntegerFieldEntry.getValue(), clientId));
            globRepository.delete(all);
        }
    }

    public void updateRepoWith(GlobRepository repository, int connectionId) {
        for (Map.Entry<GlobType, IntegerField> globTypeIntegerFieldEntry : globTypeToConnectionField.entrySet()) {
            if (globTypeIntegerFieldEntry.getValue().isKeyField()){
                GlobList all = repository.getAll(globTypeIntegerFieldEntry.getKey());
                GlobList newGlobs = new GlobList();
                for (Glob glob : all) {
                    MutableGlob instantiate = globTypeIntegerFieldEntry.getKey().instantiate();
                    for (Field field : glob.getType().getFields()) {
                        instantiate.setValue(field, glob.getValue(field));
                    }
                    instantiate.set(globTypeIntegerFieldEntry.getValue(), connectionId);
                    newGlobs.add(instantiate);
                }
                repository.delete(all);
                for (Glob newGlob : newGlobs) {
                    repository.create(newGlob);
                }
            }
            else {
                GlobList all = repository.getAll(globTypeIntegerFieldEntry.getKey());
                for (Glob glob : all) {
                    repository.update(glob.getKey(), globTypeIntegerFieldEntry.getValue(), connectionId);
                }
            }
        }
    }
}
