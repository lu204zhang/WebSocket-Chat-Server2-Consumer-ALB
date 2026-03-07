package vito.config;

import java.util.stream.IntStream;

public final class Constants {

    private Constants() {
    }

    public static final String ROOM_ID_ATTR = "roomId";
    public static final String USER_ID_ATTR = "userId";
    public static final String USERNAME_ATTR = "username";

    public static final int MIN_ROOM_ID = 1;
    public static final int MAX_ROOM_ID = 20;
    public static final String ROOM_QUEUE_PREFIX = "room.";

    public static final String[] QUEUE_NAMES = IntStream.range(MIN_ROOM_ID, MAX_ROOM_ID + 1)
            .mapToObj(i -> ROOM_QUEUE_PREFIX + i)
            .toArray(String[]::new);
}
