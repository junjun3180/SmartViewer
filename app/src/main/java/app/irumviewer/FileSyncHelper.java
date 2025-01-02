package app.irumviewer;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileSyncHelper {

    private static final String SERVER_URL = "http://IP:Port";
    private static final String TAG = "FileSyncHelper";

    public static JSONObject fetchChanges() throws Exception {
        URL url = new URL(SERVER_URL + "/changes");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        Log.d(TAG, "fetchChanges: Response code = " + responseCode);

        if (responseCode != 200) {
            throw new Exception("Server returned non-OK status: " + responseCode);
        }

        InputStream inputStream = connection.getInputStream();
        StringBuilder response = new StringBuilder();

        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            response.append(new String(buffer, 0, length));
        }

        inputStream.close();
        connection.disconnect();

        JSONObject jsonResponse = new JSONObject(response.toString());
        Log.d(TAG, "fetchChanges: Response = " + jsonResponse.toString());
        return jsonResponse;
    }

    public static void downloadFile(Context context, String fileName) throws Exception {
        String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8");
        URL url = new URL(SERVER_URL + "/file?filename=" + encodedFileName);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        Log.d("FileSyncHelper", "Response code for file " + fileName + " = " + responseCode);

        if (responseCode != 200) {
            throw new Exception("Failed to download file: " + fileName + " (status: " + responseCode + ")");
        }

        File mediaFolder = new File(context.getExternalFilesDir(null), "MyMediaFolder");
        if (!mediaFolder.exists() && !mediaFolder.mkdirs()) {
            throw new Exception("Failed to create directory: " + mediaFolder.getAbsolutePath());
        }

        File file = new File(mediaFolder, fileName);
        Log.d("FileSyncHelper", "Saving file to: " + file.getAbsolutePath());

        try (InputStream inputStream = connection.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(file)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            Log.d("FileSyncHelper", "File downloaded successfully: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e("FileSyncHelper", "Error writing file: " + file.getAbsolutePath(), e);
            throw e;
        } finally {
            connection.disconnect();
        }
    }
}

