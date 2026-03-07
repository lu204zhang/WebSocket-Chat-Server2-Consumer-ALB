package vito.config;

public final class Constants {
    private Constants() {
    }

    public static final String exchangeName = "chat.exchange";

    public static final int MIN_ROOM_ID = 1;
    public static final int MAX_ROOM_ID = 20;
    public static final String ROOM_QUEUE_PREFIX = "room.";

    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 20;

    public static final int MESSAGE_MIN_LENGTH = 1;
    public static final int MESSAGE_MAX_LENGTH = 500;

    public static final String USER_ID_PATTERN = "^[1-9]\\d{0,4}$|^100000$";
}
