package app.irumviewer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private File[] mediaFiles;
    private int currentMediaIndex = 0; // 현재 재생 중인 미디어 인덱스
    private VideoView videoView;
    private ImageView imageView;
    private EditText timeInput;
    private Button playButton;
    private Button stopButton;
    private Runnable periodicSyncTask;

    private Handler handler = new Handler(); // 미디어 재생 관리를 위한 핸들러

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 초기 레이아웃 설정 및 Edge-to-Edge 활성화
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // UI 초기화
        initializeUI(); // 추가된 호출

        // 권한 확인 및 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            } else {
                createMediaFolder();
                loadMediaFiles();
            }
        } else {
            // API 23 미만에서는 바로 폴더 생성
            // 미디어 폴더 생성 및 초기 로드
            createMediaFolder();
            loadMediaFiles();
        }

        // 앱 실행 시 최초 동기화
        syncWithServer(() -> {
            // 동기화 완료 후 미디어 파일 재생 시작
            if (mediaFiles != null && mediaFiles.length > 0) {
                playMedia(); // 파일이 있으면 재생 시작
            } else {
                Toast.makeText(this, "폴더가 비어 있습니다. 변경 사항을 주기적으로 확인합니다.", Toast.LENGTH_SHORT).show();
                setupPeriodicSync(); // 폴더가 비어 있는 경우 주기적 동기화 설정
            }
        });
    }

    // UI 요소 초기화 및 이벤트 설정
    private void initializeUI() {
        videoView = findViewById(R.id.videoView);
        imageView = findViewById(R.id.imageView);
        timeInput = findViewById(R.id.timeInput);
        playButton = findViewById(R.id.playButton);
        stopButton = findViewById(R.id.stopButton);

        // 재생 버튼 클릭 시 미디어 재생
        playButton.setOnClickListener(v -> {
            closeKeyboard();
            if (mediaFiles != null && mediaFiles.length > 0) {
                enterFullScreen();
                playMedia();
            } else {
                Toast.makeText(this, "미디어 파일이 없습니다.", Toast.LENGTH_LONG).show();
            }
        });

        // 중지 버튼 클릭 시 미디어 중지
        stopButton.setOnClickListener(v -> stopMedia());

        // 터치 이벤트로 전체 화면 모드 종료
        setupTouchListeners();
    }

    // 미디어 폴더 생성
    private void createMediaFolder() {
        File mediaFolder = new File(Environment.getExternalStorageDirectory(), "MyMediaFolder");
        if (!mediaFolder.exists()) {
            if (mediaFolder.mkdirs()) {
                Toast.makeText(this, "미디어 폴더가 생성되었습니다: " + mediaFolder.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "폴더 생성 실패: " + mediaFolder.getAbsolutePath(), Toast.LENGTH_LONG).show();
                Log.e("createMediaFolder", "폴더 생성 실패");
            }
        }
    }

    // 미디어 파일 로드
    private void loadMediaFiles() {
        File mediaFolder = new File(getExternalFilesDir(null), "MyMediaFolder");
        if (mediaFolder.exists()) {
            mediaFiles = mediaFolder.listFiles();
            if (mediaFiles != null && mediaFiles.length > 0) {
                for (File file : mediaFiles) {
                    Log.d("MediaFiles", "File found: " + file.getName());
                }
            } else {
                Log.d("MediaFiles", "No files found in the media folder.");
            }
        } else {
            Log.d("MediaFiles", "Media folder does not exist: " + mediaFolder.getAbsolutePath());
        }
    }

    // 미디어 재생
    private void playMedia() {
        if (mediaFiles == null || mediaFiles.length == 0) return;

        File currentFile = mediaFiles[currentMediaIndex];
        if (currentFile.getName().endsWith(".mp4")) {
            playVideo(currentFile);
        } else {
            playImage(currentFile);
        }
    }

    // 동영상 재생
    private void playVideo(File videoFile) {
        videoView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);

        videoView.setVideoURI(Uri.fromFile(videoFile));
        videoView.start();

        videoView.setOnCompletionListener(mp -> nextMedia());
    }

    // 사진 재생
    private void playImage(File imageFile) {
        videoView.setVisibility(View.GONE);
        imageView.setVisibility(View.VISIBLE);

        imageView.setImageURI(Uri.fromFile(imageFile));

        int duration = getDurationFromInput();
        handler.postDelayed(this::nextMedia, duration * 1000);
    }

    // 다음 미디어로 이동
    private void nextMedia() {
        currentMediaIndex++;
        if (currentMediaIndex < mediaFiles.length) {
            playMedia();
        } else {
            // 모든 파일 재생 완료
            currentMediaIndex = 0; // 인덱스 초기화
            onAllMediaPlayed();
        }
    }

    // 모든 미디어가 재생된 후 서버 동기화
    private void onAllMediaPlayed() {
        Toast.makeText(this, "모든 미디어를 재생했습니다. 서버와 동기화를 시작합니다.", Toast.LENGTH_SHORT).show();
        syncWithServer(() -> {
            // 동기화 후 재생 파일 다시 로드
            playMedia();
        });
    }

    // 미디어 중지
    private void stopMedia() {
        handler.removeCallbacksAndMessages(null);

        if (videoView.isPlaying()) {
            videoView.stopPlayback();
        }

        imageView.setVisibility(View.GONE);
        Toast.makeText(this, "재생이 중지되었습니다.", Toast.LENGTH_SHORT).show();
    }

    // 사용자 입력 시간 가져오기
    private int getDurationFromInput() {
        try {
            return Integer.parseInt(timeInput.getText().toString());
        } catch (NumberFormatException e) {
            return 5; // 기본값: 5초
        }
    }

    // 전체 화면 모드 활성화
    private void enterFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        hideButtons();
    }

    // 전체 화면 모드 종료
    private void exitFullScreen() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        showButtons();
        Toast.makeText(this, "전체 화면 모드 종료", Toast.LENGTH_SHORT).show();
    }

    // 버튼 숨기기
    private void hideButtons() {
        playButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.GONE);
    }

    // 버튼 보이기
    private void showButtons() {
        playButton.setVisibility(View.VISIBLE);
        stopButton.setVisibility(View.VISIBLE);
    }

    // 키보드 닫기
    private void closeKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // 터치 이벤트로 전체 화면 종료 설정
    private void setupTouchListeners() {
        View.OnTouchListener exitFullScreenListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                exitFullScreen();
                return true;
            }
            return false;
        };
        videoView.setOnTouchListener(exitFullScreenListener);
        imageView.setOnTouchListener(exitFullScreenListener);
    }

    // 서버와 동기화 작업
    private void syncWithServer(Runnable onComplete) {
        new FileSyncTask(this) {
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                // 동기화 완료 후 미디어 파일 다시 로드
                loadMediaFiles();
                if (onComplete != null) {
                    onComplete.run(); // 동기화 후 콜백 실행
                }
            }
        }.execute();
    }

    // 주기적으로 동기화 작업 수행
    private void setupPeriodicSync() {
        periodicSyncTask = new Runnable() {
            @Override
            public void run() {
                syncWithServer(() -> {
                    if (mediaFiles != null && mediaFiles.length > 0) {
                        handler.removeCallbacks(periodicSyncTask); // 파일이 있으면 주기적 동기화 중단
                        playMedia(); // 파일 재생 시작
                    } else {
                        handler.postDelayed(periodicSyncTask, 60000); // 1분 후 다시 실행
                    }
                });
            }
        };

        // 처음 1분 뒤 실행
        handler.postDelayed(periodicSyncTask, 60000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 액티비티가 종료될 때 핸들러의 작업 제거
        handler.removeCallbacks(periodicSyncTask);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createMediaFolder();
            } else {
                Toast.makeText(this, "권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}