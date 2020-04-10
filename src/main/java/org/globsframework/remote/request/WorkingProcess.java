package org.globsframework.remote.request;

import org.globsframework.utils.serialization.SerializedInput;
import org.globsframework.utils.serialization.SerializedOutput;

public interface WorkingProcess {

    ID getId();

    class ID {
        public final String url;
        public final String name;

        public ID(String url, String name) {
            this.url = url;
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public String getName() {
            return name;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ID id = (ID) o;

            if (name != null ? !name.equals(id.name) : id.name != null) return false;
            if (url != null ? !url.equals(id.url) : id.url != null) return false;

            return true;
        }

        public int hashCode() {
            int result = url != null ? url.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }

        public String toString() {
            return name + " : " + url;
        }

        public static void write(SerializedOutput output, ID id){
            if (id != null){
                output.write(true);
                output.writeUtf8String(id.getUrl());
                output.writeUtf8String(id.getName());
            }
            else {
                output.write(false);
            }
        }

        public static ID read(SerializedInput input){
            if (input.readBoolean()){
                String url = input.readUtf8String();
                String name = input.readUtf8String();
                return new ID(url, name);
            }
            else {
                return null;
            }
        }

    }
}
