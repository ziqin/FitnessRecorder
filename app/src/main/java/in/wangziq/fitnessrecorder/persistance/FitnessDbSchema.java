package in.wangziq.fitnessrecorder.persistance;

public final class FitnessDbSchema {

    public static final class HeartRateTable {
        public static final String NAME = "heart_rate";

        public static final class Cols {
            public static final String timestamp = "timestamp";
            public static final String heartRate = "rate";
        }
    }

    public static final class AccelerationTable {
        public static final String NAME = "acceleration";

        public static final class Cols {
            public static final String id = "id";
            public static final String timestamp = "timestamp";
            public static final String x = "x_axis";
            public static final String y = "y_axis";
            public static final String z = "z_axis";
        }
    }

}
