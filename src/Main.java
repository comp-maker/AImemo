import javafx.application.Application;
import javafx.concurrent.Worker.State;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main extends Application {

    // HTML에서 window.java.saveMemo(...)로 호출할 다리(브릿지) 역할
    public static class MemoBridge {
        private final Path memoDir;

        public MemoBridge() {
            // 사용자 홈 폴더 아래에 MemoApp/memos 디렉토리 생성 (권한 문제 최소화)
            Path base = Paths.get(System.getProperty("user.home"), "MemoApp", "memos");
            this.memoDir = base;
            try {
                Files.createDirectories(memoDir);
                System.out.println("메모 디렉토리 생성됨: " + memoDir.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("메모 디렉토리 생성 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // HTML에서 전달받은 텍스트를 파일로 저장 (파일명 지정 가능)
        public void saveMemo(String content, String filename) {
            System.out.println("=== saveMemo 호출됨 ===");
            System.out.println("받은 content 길이: " + (content != null ? content.length() : 0));
            System.out.println("받은 filename: '" + filename + "'");
            System.out.println("memoDir 경로: " + memoDir.toAbsolutePath());
            
            String finalFilename;
            
            if (filename != null && !filename.trim().isEmpty()) {
                // 사용자가 지정한 파일명 사용
                finalFilename = filename.trim() + ".txt";
                System.out.println("사용자 지정 파일명 사용: " + finalFilename);
            } else {
                // 파일명이 없으면 타임스탬프 사용
                LocalDateTime now = LocalDateTime.now();
                String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                finalFilename = "memo_" + timestamp + ".txt";
                System.out.println("타임스탬프 파일명 사용: " + finalFilename);
            }
            
            Path file = memoDir.resolve(finalFilename);
            System.out.println("최종 파일 경로: " + file.toAbsolutePath());
            System.out.println("디렉토리 존재 여부: " + Files.exists(memoDir));
            System.out.println("디렉토리 쓰기 권한: " + Files.isWritable(memoDir));
            
            try {
                // 디렉토리가 없으면 생성
                if (!Files.exists(memoDir)) {
                    Files.createDirectories(memoDir);
                    System.out.println("디렉토리 생성됨: " + memoDir.toAbsolutePath());
                }
                
                // 파일 저장
                try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    bw.write(content == null ? "" : content);
                    System.out.println("✅ 메모 저장 성공: " + file.toAbsolutePath());
                    System.out.println("저장된 파일 크기: " + Files.size(file) + " bytes");
                }
            } catch (IOException e) {
                System.err.println("❌ 메모 저장 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // 기존 호환성을 위한 오버로드 메서드
        public void saveMemo(String content) {
            saveMemo(content, null);
        }
        
        // 메모 목록을 JSON 형태로 반환
        public String getMemoList() {
            try {
                List<String> memoFiles = Files.list(memoDir)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
                
                // JSON 형태로 변환
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < memoFiles.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append("\"").append(memoFiles.get(i)).append("\"");
                }
                json.append("]");
                
                System.out.println("메모 목록 반환: " + json.toString());
                return json.toString();
            } catch (IOException e) {
                System.err.println("메모 목록 읽기 실패: " + e.getMessage());
                e.printStackTrace();
                return "[]";
            }
        }
        
        // 특정 메모 파일의 내용을 읽어서 반환
        public String loadMemo(String filename) {
            System.out.println("=== loadMemo 호출됨 ===");
            System.out.println("요청된 파일명: " + filename);
            
            try {
                Path file = memoDir.resolve(filename);
                System.out.println("전체 파일 경로: " + file.toAbsolutePath());
                System.out.println("파일 존재 여부: " + Files.exists(file));
                
                if (Files.exists(file)) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    System.out.println("✅ 메모 불러오기 성공: " + filename + " (길이: " + content.length() + ")");
                    System.out.println("내용 미리보기: " + content.substring(0, Math.min(50, content.length())) + (content.length() > 50 ? "..." : ""));
                    return content;
                } else {
                    System.err.println("❌ 파일이 존재하지 않음: " + filename);
                    return "";
                }
            } catch (IOException e) {
                System.err.println("❌ 메모 불러오기 실패: " + e.getMessage());
                e.printStackTrace();
                return "";
            }
        }
        
        // 메모 파일 삭제
        public boolean deleteMemo(String filename) {
            System.out.println("=== deleteMemo 호출됨 ===");
            System.out.println("삭제할 파일명: " + filename);
            System.out.println("memoDir 경로: " + memoDir.toAbsolutePath());
            
            try {
                Path file = memoDir.resolve(filename);
                System.out.println("삭제할 파일 경로: " + file.toAbsolutePath());
                System.out.println("파일 존재 여부: " + Files.exists(file));
                System.out.println("디렉토리 존재 여부: " + Files.exists(memoDir));
                System.out.println("디렉토리 쓰기 권한: " + Files.isWritable(memoDir));
                
                if (Files.exists(file)) {
                    // 파일 크기 확인
                    long fileSize = Files.size(file);
                    System.out.println("파일 크기: " + fileSize + " bytes");
                    
                    // 파일 삭제
                    boolean deleted = Files.deleteIfExists(file);
                    System.out.println("삭제 결과: " + deleted);
                    
                    if (deleted) {
                        System.out.println("✅ 메모 삭제 성공: " + filename);
                        return true;
                    } else {
                        System.err.println("❌ 파일 삭제 실패: " + filename);
                        return false;
                    }
                } else {
                    System.err.println("❌ 삭제할 파일이 존재하지 않음: " + filename);
                    return false;
                }
            } catch (IOException e) {
                System.err.println("❌ 메모 삭제 실패: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        // 메모 파일 이름 변경
        public boolean renameMemo(String oldFilename, String newFilename) {
            System.out.println("=== renameMemo 호출됨 ===");
            System.out.println("이전 파일명: " + oldFilename);
            System.out.println("새 파일명: " + newFilename);
            
            try {
                Path oldFile = memoDir.resolve(oldFilename);
                Path newFile = memoDir.resolve(newFilename + ".txt");
                
                System.out.println("이전 파일 경로: " + oldFile.toAbsolutePath());
                System.out.println("새 파일 경로: " + newFile.toAbsolutePath());
                
                if (Files.exists(oldFile)) {
                    if (Files.exists(newFile)) {
                        System.err.println("❌ 새 파일명이 이미 존재함: " + newFilename);
                        return false;
                    }
                    
                    Files.move(oldFile, newFile);
                    System.out.println("✅ 메모 이름 변경 성공: " + oldFilename + " → " + newFilename + ".txt");
                    return true;
                } else {
                    System.err.println("❌ 변경할 파일이 존재하지 않음: " + oldFilename);
                    return false;
                }
            } catch (IOException e) {
                System.err.println("❌ 메모 이름 변경 실패: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
    }

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

        // JavaScript에서 Java 호출 허용 설정
        engine.setJavaScriptEnabled(true);
        
        // 보안 정책 설정 (개발용)
        System.setProperty("javafx.web.allow.unsafe.javascript", "true");

        // 리소스(memo.html) 불러오기 - 경로 수정
        String url = getClass().getResource("/memo.html").toExternalForm();
        if (url == null) {
            // 대안 경로 시도
            url = getClass().getResource("resources/memo.html").toExternalForm();
        }
        if (url == null) {
            // 절대 경로로 시도
            try {
                Path htmlPath = Paths.get("src", "resources", "memo.html").toAbsolutePath();
                url = htmlPath.toUri().toURL().toExternalForm();
                System.out.println("절대 경로로 HTML 파일 로드: " + url);
            } catch (Exception e) {
                System.err.println("HTML 파일을 찾을 수 없습니다. 경로를 확인해주세요.");
                e.printStackTrace();
                return;
            }
        }
        System.out.println("HTML 파일 URL: " + url);
        engine.load(url);

        // 페이지 로드 완료되면 JS window에 java 브릿지 주입
        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            System.out.println("페이지 로드 상태: " + newState);
            if (newState == State.SUCCEEDED) { // 문서 로드 완료
                try {
                    // 잠시 대기 후 브릿지 설정 (JavaScript 엔진 초기화 대기)
                    Thread.sleep(100);
                    
                    JSObject window = (JSObject) engine.executeScript("window");
                    MemoBridge bridge = new MemoBridge();
                    window.setMember("java", bridge);
                    System.out.println("Java 브릿지 설정 완료");
                    
                    // 브릿지가 제대로 설정되었는지 확인
                    engine.executeScript("console.log('Java bridge test:', window.java);");
                    
                    // JavaScript에서 직접 테스트 호출
                    engine.executeScript("if(window.java && window.java.saveMemo) { console.log('Java bridge is ready'); } else { console.log('Java bridge not ready'); }");
                    
                    // 추가 함수들 확인
                    engine.executeScript("console.log('deleteMemo exists:', !!(window.java && window.java.deleteMemo));");
                    engine.executeScript("console.log('renameMemo exists:', !!(window.java && window.java.renameMemo));");
                    engine.executeScript("console.log('getMemoList exists:', !!(window.java && window.java.getMemoList));");
                    
                } catch (Exception e) {
                    System.err.println("Java 브릿지 설정 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        stage.setTitle("로컬 메모장 (기본)");
        stage.setScene(new Scene(webView, 720, 480));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

