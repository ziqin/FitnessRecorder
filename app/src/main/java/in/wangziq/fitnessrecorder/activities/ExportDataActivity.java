package in.wangziq.fitnessrecorder.activities;

import android.Manifest;

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.annimon.stream.function.Function;
import com.opencsv.CSVWriter;

import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;

import in.wangziq.fitnessrecorder.R;
import in.wangziq.fitnessrecorder.persistance.DbTool;
import in.wangziq.fitnessrecorder.persistance.FitnessDbHelper;
import in.wangziq.fitnessrecorder.databinding.ActivityExportDataBinding;

public final class ExportDataActivity extends AppCompatActivity {

    private static final String TAG = ExportDataActivity.class.getSimpleName();
    private static final int WRITE_EXTERNAL_REQUEST = 1;

    private ActivityExportDataBinding mBinding;
    private File mAppDir;
    private DbTool mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_export_data);

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        mAppDir = new File(downloadDir, getString(R.string.app_name));
        mBinding.exportTip.setText(getString(R.string.export_tip, mAppDir.toString()));

        mDatabase = new DbTool(this);

        mBinding.btnExport.setOnClickListener(view -> requestWriteExternalPermissionAndExport());
    }


    // FIXME: sync -> async
    private void export() {
        String filenamePrefix = "exported_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        if (mBinding.radioButtonCsv.isChecked()) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
            if (mBinding.checkboxHeartRate.isChecked()) {
                String filename = filenamePrefix + "_heartRate.csv";
                saveCsvFile(mDatabase.queryAllHeartRate(), new File(mAppDir, filename), cursor -> new String[] {
                        cursor.getString(0),
                        Integer.toString(cursor.getInt(1))
                });
                Log.i(TAG, "exported: " + filename);
            }
            if (mBinding.checkboxAcceleration.isChecked()) {
                String filename = filenamePrefix + "_acceleration.csv";
                saveCsvFile(mDatabase.queryAllAcceleration(), new File(mAppDir, filename), cursor -> new String[] {
                        Long.toString(cursor.getLong(0)),
                        cursor.getString(1),
                        Float.toString(cursor.getFloat(2)),
                        Float.toString(cursor.getFloat(3)),
                        Float.toString(cursor.getFloat(4))
                });
                Log.i(TAG, "exported: " + filename);
            }
            Snackbar.make(getRootView(), R.string.toast_export_succeed, Snackbar.LENGTH_SHORT).show();
        } else if (mBinding.radioButtonSqlite.isChecked()) {
            saveDbFile(new File(mAppDir, filenamePrefix + "_all.db"));
        }
    }

    private void saveCsvFile(Cursor cursor, File path, Function<Cursor, String[]> getRow) {
        try {
            path.createNewFile();
            CSVWriter csvWriter = new CSVWriter(new BufferedWriter(new FileWriter(path)));
            csvWriter.writeNext(cursor.getColumnNames());
            while (cursor.moveToNext())
                csvWriter.writeNext(getRow.apply(cursor));
            csvWriter.close();
            cursor.close();
            Log.i(TAG, "saveCsvFile: succeeded");
        } catch (IOException e) {
            Log.e(TAG, "saveCsvFile: failed", e);
        } catch (Exception e) {
            Log.e(TAG, "saveCsvFile: unknown exception", e);
        }
    }

    // TODO: use an intentService to copy files
    private void saveDbFile(File destinationPath) {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File srcPath = getDatabasePath(FitnessDbHelper.DB_NAME);
            try {
                FileUtils.copyFile(srcPath, destinationPath, true);
                Snackbar.make(getRootView(), R.string.toast_export_succeed, Snackbar.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "saveDbFile: failed to copy " + srcPath + " to " + destinationPath, e);
                Snackbar.make(getRootView(), R.string.toast_export_copy_fail, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.i(TAG, "saveDbFile: external storage unavailable");
            Toast.makeText(this, R.string.toast_export_state_fail, Toast.LENGTH_LONG).show();
        }
    }


    /**
     * @return true if permission is ready
     */
    private void requestWriteExternalPermissionAndExport() {
        final String PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            export();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION)) {
                Snackbar.make(getRootView(), R.string.toast_need_write_external, Snackbar.LENGTH_SHORT).show();
                Log.i(TAG, "requestWriteExternalPermission: denied in the past");
            } else {
                ActivityCompat.requestPermissions(this, new String[] {PERMISSION}, WRITE_EXTERNAL_REQUEST);
                Log.i(TAG, "requestWriteExternalPermission: requesting");
            }
        }
    }


    private View getRootView() {
        return getWindow().getDecorView().getRootView();
    }
}
