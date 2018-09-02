package in.wangziq.fitnessrecorder.persistance;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public final class DbTool {

    private static final String TAG = DbTool.class.getSimpleName();

    private SQLiteDatabase mHeartRateDb;

    public DbTool(Context context) {
        mHeartRateDb = new FitnessDbHelper(context.getApplicationContext()).getWritableDatabase();
    }

    public void insertHeartRate(int heartRate) {
        ContentValues value = new ContentValues();
        value.put(FitnessDbSchema.HeartRateTable.Cols.heartRate, heartRate);
        mHeartRateDb.insert(FitnessDbSchema.HeartRateTable.NAME, null, value);
        Log.d(TAG, "insertHeartRate: inserted " + heartRate);
    }

    public void insertAcceleration(float x, float y, float z) {
        ContentValues values = new ContentValues();
        values.put(FitnessDbSchema.AccelerationTable.Cols.x, x);
        values.put(FitnessDbSchema.AccelerationTable.Cols.y, y);
        values.put(FitnessDbSchema.AccelerationTable.Cols.z, z);
        mHeartRateDb.insert(FitnessDbSchema.AccelerationTable.NAME, null, values);
        Log.d(TAG, String.format("insertAcceleration: inserted (x=%f, y=%f, z=%f)", x, y, z));
    }

}
