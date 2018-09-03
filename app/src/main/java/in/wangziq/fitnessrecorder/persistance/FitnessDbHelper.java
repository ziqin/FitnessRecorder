package in.wangziq.fitnessrecorder.persistance;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public final class FitnessDbHelper extends SQLiteOpenHelper {

    private static final String TAG = FitnessDbHelper.class.getSimpleName();

    public static final int VERSION = 2;
    public static final String DB_NAME = "fitness_data.db";

    public FitnessDbHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createHeartRateDb(db);
        createAccelerationDb(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
    }

    private static void createHeartRateDb(SQLiteDatabase db) {
        final String ddl = String.format(
                "create table %s (%s datetime primary key default current_timestamp, %s integer not null check (%s > 0));",
                FitnessDbSchema.HeartRateTable.NAME,
                FitnessDbSchema.HeartRateTable.Cols.timestamp,
                FitnessDbSchema.HeartRateTable.Cols.heartRate, FitnessDbSchema.HeartRateTable.Cols.heartRate);
        db.execSQL(ddl);
        Log.i(TAG, "createHeartRateDb: created successfully");
    }

    private static void createAccelerationDb(SQLiteDatabase db) {
        final String ddl = String.format("create table %s (" +
                "%s integer primary key autoincrement, " +
                "%s datetime not null default current_timestamp, " +
                "%s real not null, " +
                "%s real not null, " +
                "%s real not null);",
                FitnessDbSchema.AccelerationTable.NAME,
                FitnessDbSchema.AccelerationTable.Cols.id,
                FitnessDbSchema.AccelerationTable.Cols.timestamp,
                FitnessDbSchema.AccelerationTable.Cols.x,
                FitnessDbSchema.AccelerationTable.Cols.y,
                FitnessDbSchema.AccelerationTable.Cols.z);
        db.execSQL(ddl);
        Log.i(TAG, "createAccelerationDb: created successfully");
    }

}
