package in.wangziq.fitnessrecorder.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import in.wangziq.fitnessrecorder.R;
import in.wangziq.fitnessrecorder.persistance.FitnessDbHelper;

public final class ExportDataActivity extends AppCompatActivity {

    private static final String TAG = ExportDataActivity.class.getSimpleName();
    private static final int WRITE_EXTERNAL_REQUEST = 1;

    private File mAppDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export_data);

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        mAppDir = new File(downloadDir, getString(R.string.app_name));

        TextView noticeText = findViewById(R.id.export_tip);
        noticeText.setText(getString(R.string.export_tip, mAppDir.toString()));

        Button exportButton = findViewById(R.id.btn_export);
        exportButton.setOnClickListener(view -> new Thread(this::requestWriteExternalPermissionAndExport).run());
    }

    private void requestWriteExternalPermissionAndExport() {
        final String PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PERMISSION)) {
                Toast.makeText(this, R.string.toast_need_write_external, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "requestWriteExternalPermission: denied in the past");
            } else {
                ActivityCompat.requestPermissions(this, new String[] {PERMISSION}, WRITE_EXTERNAL_REQUEST);
                Log.i(TAG, "requestWriteExternalPermission: requesting");
            }
        } else {
            Log.i(TAG, "requestLocationPermissionAndScanBt: permission already granted");
            saveDbFile();
        }
    }

    // TODO: use an intentService to copy files
    private void saveDbFile() {
        File srcPath, destPath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            destPath = new File(mAppDir, "db_exported_" + date + ".db");
            srcPath = getDatabasePath(FitnessDbHelper.DB_NAME);
            try {
                FileUtils.copyFile(srcPath, destPath, true);
                String successMsg = getString(R.string.toast_export_succeed) + destPath;
                Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "saveDbFile: failed to copy " + srcPath + " to " + destPath, e);
                Toast.makeText(this, R.string.toast_export_copy_fail, Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.i(TAG, "saveDbFile: external storage unavailable");
            Toast.makeText(this, R.string.toast_export_state_fail, Toast.LENGTH_SHORT).show();
        }
    }
}
