package app.irumviewer;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;

public class FileSyncTask extends AsyncTask<Void, Void, Void> {

    private final WeakReference<Context> contextRef; // WeakReference로 Context 참조
    private boolean hasError = false; // 동기화 중 오류 여부 플래그

    public FileSyncTask(Context context) {
        this.contextRef = new WeakReference<>(context);
    }

    @Override
    protected Void doInBackground(Void... voids) {
        try {
            // 서버에서 변경 및 삭제된 파일 목록 가져오기
            JSONObject changes = FileSyncHelper.fetchChanges();
            JSONArray changedFiles = changes.getJSONArray("changed_files");
            JSONArray deletedFiles = changes.getJSONArray("deleted_files");

            // 변경된 파일 다운로드
            for (int i = 0; i < changedFiles.length(); i++) {
                String fileName = changedFiles.getString(i);
                try {
                    FileSyncHelper.downloadFile(contextRef.get(), fileName);
                } catch (Exception e) {
                    Log.e("FileSyncTask", "Error downloading file: " + fileName, e);
                    hasError = true;
                }
            }

            // 삭제된 파일 제거
            for (int i = 0; i < deletedFiles.length(); i++) {
                String fileName = deletedFiles.getString(i);
                try {
                    deleteFileFromDevice(contextRef.get(), fileName);
                } catch (Exception e) {
                    Log.e("FileSyncTask", "Error deleting file: " + fileName, e);
                    hasError = true;
                }
            }
        } catch (Exception e) {
            Log.e("FileSyncTask", "Error fetching changes", e);
            hasError = true;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);

        Context context = contextRef.get();
        if (context == null) {
            return; // Context가 GC에 의해 수거된 경우, 작업 종료
        }

        if (hasError) {
            Toast.makeText(context, "동기화 완료했지만 일부 파일에 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "서버 동기화가 완료되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        Context context = contextRef.get();
        if (context == null) {
            return; // Context가 GC에 의해 수거된 경우, 작업 종료
        }

        Toast.makeText(context, "서버 동기화 작업이 취소되었습니다.", Toast.LENGTH_SHORT).show();
    }

    // 안드로이드 기기에서 파일 삭제
    private void deleteFileFromDevice(Context context, String fileName) {
        File mediaFolder = new File(context.getExternalFilesDir(null), "MyMediaFolder");
        File file = new File(mediaFolder, fileName);

        if (file.exists() && file.delete()) {
            Log.d("FileSyncTask", "File deleted: " + file.getAbsolutePath());
        } else {
            Log.e("FileSyncTask", "Failed to delete file: " + file.getAbsolutePath());
        }
    }
}
