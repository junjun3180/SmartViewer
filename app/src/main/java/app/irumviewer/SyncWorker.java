package app.irumviewer;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // 1. 변경된 파일 및 삭제된 파일 목록 가져오기
            JSONObject changes = FileSyncHelper.fetchChanges();
            JSONArray changedFiles = changes.getJSONArray("changed_files");
            JSONArray deletedFiles = changes.getJSONArray("deleted_files");

            // 2. 변경된 파일 다운로드
            for (int i = 0; i < changedFiles.length(); i++) {
                String fileName = changedFiles.getString(i);
                FileSyncHelper.downloadFile(getApplicationContext(), fileName);
            }

            // 3. 삭제된 파일 제거
            for (int i = 0; i < deletedFiles.length(); i++) {
                String fileName = deletedFiles.getString(i);
                deleteFileFromDevice(fileName);
            }

            showToast("파일 동기화가 완료되었습니다.");
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            showToast("서버 연결에 실패했습니다.");
            return Result.failure();
        }
    }

    // 기기에서 파일 삭제
    private void deleteFileFromDevice(String fileName) {
        File mediaFolder = new File(getApplicationContext().getExternalFilesDir(null), "MyMediaFolder");
        File file = new File(mediaFolder, fileName);

        if (file.exists() && file.delete()) {
            Log.d("SyncWorker", "File deleted: " + file.getAbsolutePath());
        } else {
            Log.e("SyncWorker", "Failed to delete file: " + file.getAbsolutePath());
        }
    }

    // UI 스레드에서 Toast 메시지 표시
    private void showToast(String message) {
        new android.os.Handler(android.os.Looper.getMainLooper())
                .post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }
}
