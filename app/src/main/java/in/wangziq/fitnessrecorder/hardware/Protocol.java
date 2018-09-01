package in.wangziq.fitnessrecorder.hardware;


public final class Protocol {

    public static final String BT_BROADCAST_NAME = "MI Band 2";

    private final static String BASE = "0000%04x-0000-1000-8000-00805f9b34fb";
    private final static String BASE2 = "0000%04x-0000-3512-2118-0009af100700";

    public final static class Service {
        public final static String BASIC              = String.format(BASE, 0xfee0);
        public final static String AUTH               = String.format(BASE, 0xfee1);
        public final static String ALERT              = String.format(BASE, 0x1802);
        public final static String ALERT_NOTIFICATION = String.format(BASE, 0x1811);
        public final static String HEART_RATE         = String.format(BASE, 0x180d);
        public final static String DEVICE_INFO        = String.format(BASE, 0x180a);
    }

    public final static class Characteristic {
        public final static String HZ                  = String.format(BASE2, 0x0002);
        public final static String SENSOR_CONTROL      = String.format(BASE2, 0x0001);
        public final static String SENSOR_DATA         = String.format(BASE2, 0x0002);
        public final static String AUTH                = String.format(BASE2, 0x0009);
        public final static String BATTERY             = String.format(BASE2, 0x0006);
        public final static String STEPS               = String.format(BASE2, 0x0007);
        public final static String HEART_RATE_MEASURE  = String.format(BASE, 0x2a37);
        public final static String HEART_RATE_CONTROL  = String.format(BASE, 0x2a39);
        public final static String ALERT               = String.format(BASE, 0x2a06);
        public final static String LE_PARAMS           = String.format(BASE, 0xff09);

        public final static int REVISION         = 0x2a28;
        public final static int SERIAL           = 0x2a25;
        public final static int HRDW_REVISION    = 0x2a27;
        public final static String CONFIGURATION = String.format(BASE2, 0x0003);
        public final static String DEVICEEVENT   = String.format(BASE2, 0x0010);

        public final static String CURRENT_TIME  = String.format(BASE, 0x2A2B);
        public final static String AGE           = String.format(BASE, 0x2A80);
        public final static String USER_SETTINGS = String.format(BASE2, 0x0008);

    }

//    public final static String NOTIFICATION_DESCRIPTOR = String.format(BASE, 0x2902);

    public final static class Command {
        public static final byte[] SEND_KEY = {0x01, 0x00};
        public static final byte[] RAND_REQUEST         = {0x02, 0x00};
        public static final byte[] SEND_ENCRYPTED = {0x03, 0x00};

        public static final byte[] HEART_STOP_CONTINUOUS    = {0x15, 0x01, 0x00};
        public static final byte[] HEART_START_CONTINUOUS = {0x15, 0x01, 0x01};
        public static final byte[] HEART_STOP_MANUAL        = {0x15, 0x02, 0x00};
        public static final byte[] HEART_START_MANUAL       = {0x15, 0x02, 0x01};
        public static final byte[] HEART_KEEP_ALIVE         = {0x16};

        public static final byte[] ACCELERATION_INIT = {0x01, 0x01, 0x19}; // 0x01, 0x02, 0x19 (heart rate) / 0x01, 0x03, 0x19
        public static final byte[] ACCELERATION_START = {0x02};
        public static final byte[] ACCELERATION_STOP = {0x03};  // or 0x02
    }

    public final static class Response {
        public static final int SEND_KEY_OK = 0x100101;
        public static final int SEND_KEY_OOPS = 0x100104;
        public static final int RAND_OK = 0x100201;
        public static final int RAND_OOPS = 0x100204;
        public static final int AUTH_OK = 0x100301;
        public static final int AUTH_OOPS = 0x100304;
    }

    public final static class Time {
        public static final int HEART_KEEP_ALIVE_PERIOD = 10000; // 10 s
        public static final int ACCELERATION_PERIOD = 65000; // automatically stop every 70s
    }

}
