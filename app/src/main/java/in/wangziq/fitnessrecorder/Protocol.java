package in.wangziq.fitnessrecorder;


public final class Protocol {

    private final static String BASE = "0000%04x-0000-1000-8000-00805f9b34fb";
    private final static String BASE2 = "0000%04x-0000-3512-2118-0009af100700";

    public final static String SERVICE_BASIC              = String.format(BASE, 0xfee0);
    public final static String SERVICE_AUTH               = String.format(BASE, 0xfee1);
    public final static String SERVICE_ALERT              = String.format(BASE, 0x1802);
    public final static String SERVICE_ALERT_NOTIFICATION = String.format(BASE, 0x1811);
    public final static String SERVICE_HEART_RATE         = String.format(BASE, 0x180d);
    public final static String SERVICE_DEVICE_INFO        = String.format(BASE, 0x180a);

    public final static String CHARACTERISTIC_HZ                  = String.format(BASE2, 0x0002);
    public final static String CHARACTERISTIC_SENSOR              = String.format(BASE2, 0x0001);
    public final static String CHARACTERISTIC_AUTH                = String.format(BASE2, 0x0009);
    public final static String CHARACTERISTIC_BATTERY             = String.format(BASE2, 0x0006);
    public final static String CHARACTERISTIC_STEPS               = String.format(BASE2, 0x0007);
    public final static String CHARACTERISTIC_HEART_RATE_MEASURE  = String.format(BASE, 0x2a37);
    public final static String CHARACTERISTIC_HEART_RATE_CONTROL  = String.format(BASE, 0x2a39);
    public final static String CHARACTERISTIC_ALERT               = String.format(BASE, 0x2a06);
    public final static String CHARACTERISTIC_LE_PARAMS           = String.format(BASE, 0xff09);

    public final static int CHARACTERISTIC_REVISION         = 0x2a28;
    public final static int CHARACTERISTIC_SERIAL           = 0x2a25;
    public final static int CHARACTERISTIC_HRDW_REVISION    = 0x2a27;
    public final static String CHARACTERISTIC_CONFIGURATION = String.format(BASE2, 0x0003);
    public final static String CHARACTERISTIC_DEVICEEVENT   = String.format(BASE2, 0x0010);

    public final static String CHARACTERISTIC_CURRENT_TIME  = String.format(BASE, 0x2A2B);
    public final static String CHARACTERISTIC_AGE           = String.format(BASE, 0x2A80);
    public final static String CHARACTERISTIC_USER_SETTINGS = String.format(BASE2, 0x0008);

//    public final static String NOTIFICATION_DESCRIPTOR = String.format(BASE, 0x2902);

    public static final byte[] SEND_KEY_CMD         = {0x01, 0x00};
    public static final byte[] RAND_REQUEST         = {0x02, 0x00};
    public static final byte[] SEND_ENCRYPTED_CMD   = {0x03, 0x00};

    public static final int SEND_KEY_RESPONSE_OK   = 0x100101;
    public static final int SEND_KEY_RESPONSE_OOPS = 0x100104;
    public static final int RAND_RESPONSE_OK       = 0x100201;
    public static final int RAND_RESPONSE_OOPS     = 0x100204;
    public static final int AUTH_RESPONSE_OK       = 0x100301;
    public static final int AUTH_RESPONSE_OOPS     = 0x100304;

    public static final byte[] HEART_STOP_CONTINUOUS    = {0x15, 0x01, 0x00};
    public static final byte[] HEART_START_CONTIUOUS    = {0x15, 0x01, 0x01};
    public static final byte[] HEART_STOP_MANUAL        = {0x15, 0x02, 0x00};
    public static final byte[] HEART_START_MANUAL       = {0x15, 0x02, 0x01};
    public static final byte[] HEART_KEEP_ALIVE         = {0x16};

    public static final int HEART_KEEP_ALIVE_PERIOD = 12000;

}
