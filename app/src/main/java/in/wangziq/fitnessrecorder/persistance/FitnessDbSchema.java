package in.wangziq.fitnessrecorder.persistance;

public final class FitnessDbSchema {

    public static final class HeartRateTable {
        public static final String NAME = "heart_rate";

        public static final class Cols {
            public static final String timestamp = "timestamp";
            public static final String heartRate = "rate";
        }
    }

}
