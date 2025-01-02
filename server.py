from flask import Flask, jsonify, request, send_from_directory
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
import os
import threading
import time

app = Flask(__name__)

# 감시할 폴더 경로
WATCH_FOLDER = r"C:\watchdog"
changed_files = set()  # 변경된 파일 목록
deleted_files = set()  # 삭제된 파일 목록


# Watchdog 이벤트 핸들러
class FolderHandler(FileSystemEventHandler):
    def on_modified(self, event):
        if not event.is_directory:
            changed_files.add(os.path.basename(event.src_path))

    def on_created(self, event):
        if not event.is_directory:
            changed_files.add(os.path.basename(event.src_path))

    def on_deleted(self, event):
        if not event.is_directory:
            deleted_files.add(os.path.basename(event.src_path))


# Watchdog 폴더 감시 시작
def start_watcher():
    observer = Observer()
    handler = FolderHandler()
    observer.schedule(handler, WATCH_FOLDER, recursive=True)
    observer.start()
    print(f"Started watching {WATCH_FOLDER}")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    observer.join()


# API: 변경된 파일 및 삭제된 파일 목록 반환
@app.route('/changes', methods=['GET'])
def get_changes():
    global changed_files, deleted_files
    response = {
        "changed_files": list(changed_files),
        "deleted_files": list(deleted_files)
    }
    changed_files.clear()
    deleted_files.clear()
    return jsonify(response)


# API: 특정 파일 전송
@app.route('/file', methods=['GET'])
def get_file():
    filename = request.args.get('filename')
    return send_from_directory(WATCH_FOLDER, filename, as_attachment=True)


if __name__ == '__main__':
    # 서버와 Watchdog 실행
    threading.Thread(target=start_watcher, daemon=True).start()
    app.run(host="0.0.0.0", port=5000)
