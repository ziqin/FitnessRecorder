package in.wangziq.fitnessrecorder.config;

public final class Constants {

    private static final String BASE = "in.wangziq.fitnessrecorder.";

    public static final class Action {
        public static final String STATE_UPDATE = BASE + "services.action.GET_STATE";
        public static final String PAIR = BASE + "services.action.PAIR";
        public static final String CONNECT = BASE + "services.action.CONNECT";
        public static final String DISCONNECT = BASE + "services.action.DISCONNECT";
        public static final String START_HEART_RATE = BASE + "services.action.START_HEART_RATE";
        public static final String STOP_HEART_RATE = BASE + "services.action.STOP_HEART_RATE";
        public static final String BROADCAST_HEART_RATE = BASE + "services.action.BROADCAST_HEART_RATE";
        public static final String START_ACCELERATION = BASE + "services.action.START_ACCELERATION";
        public static final String STOP_ACCELERATION = BASE + "services.action.STOP_ACCELERATION";
        public static final String BROADCAST_ACCELERATION = BASE + "services.action.BROADCAST_ACCELERATION";
    }

    public static final class Extra {
        public static final String MAC = "extra.data.MAC";
        public static final String KEY = "extra.data.KEY";
        public static final String STATE = "extra.data.STATE";
        public static final String HEART_RATE = "extra.data.HEART_RATE";
        public static final String WITH_RESPONSE = "extra.data.WITH_RESPONSE";
        public static final String STATUS = "extra.response.STATUS";
        public static final String ACCELERATION_X = "extra.data.acceleration_x";
        public static final String ACCELERATION_Y = "extra.data.acceleration_y";
        public static final String ACCELERATION_Z = "extra.data.acceleration_z";
    }

    public static final class Status {
        public static final int OK = 0;
        public static final int FAILED = -1;
        public static final int UNKNOWN = -2;

    }

    public static final class Settings {
        public static final String DEVICE = "device";
        public static final String DEVICE_KEY = DEVICE + ".KEY";
        public static final String DEVICE_MAC = DEVICE + ".MAC";
    }

}
