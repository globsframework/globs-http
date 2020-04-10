package org.globsframework.remote;

public class SystemUtils {

    public static final long DURATION = 1000l * 60l * 60l * 24l * 7l;

//        int serverId = (int) Math.IEEEremainder(l, 1000 * 60 * 60 * 24 * 7); // sur 7 jours

    public static int createIdFromCurrentTimeMillis() {
        long l = System.currentTimeMillis();
        long near = l / DURATION;
        return (int) (l - (near * DURATION)) + 1; // remove zero
    }
}
