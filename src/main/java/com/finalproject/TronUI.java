package com.finalproject;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.OutputStream;
import java.io.PrintStream;

public class TronUI extends Application {

    private TextArea consoleTextArea;
    private Button startButton;
    private Button stopButton;
    private Label statusLabel;
    private Thread backgroundThread;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("TronClass Auto Rollcall");

        // UI Components
        startButton = new Button("▶ 開始掛機");
        stopButton = new Button("⏹ 停止");
        stopButton.setDisable(true); // initially disabled
        
        statusLabel = new Label("狀態: 等待啟動...");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555555;");

        HBox controlBox = new HBox(10, startButton, stopButton, statusLabel);
        controlBox.setPadding(new Insets(10));
        controlBox.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");
        controlBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // API Tester UI
        ComboBox<String> methodCombo = new ComboBox<>();
        methodCombo.getItems().addAll("GET", "POST", "PUT", "DELETE");
        methodCombo.setValue("GET");

        TextField urlField = new TextField("/api/user/recently-visited-courses");
        urlField.setPromptText("URL (e.g., /api/...)");
        HBox.setHgrow(urlField, Priority.ALWAYS);

        Button sendApiButton = new Button("送出請求");

        HBox apiHeader = new HBox(10, methodCombo, urlField, sendApiButton);
        apiHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextArea bodyArea = new TextArea();
        bodyArea.setPromptText("Request Body (僅 POST/PUT 需要填寫 JSON)");
        bodyArea.setPrefRowCount(3);
        bodyArea.setStyle("-fx-font-family: 'Consolas', monospace;");

        VBox apiBox = new VBox(5, apiHeader, bodyArea);
        apiBox.setPadding(new Insets(10));
        
        TitledPane apiPane = new TitledPane("自訂 API 測試 (展開/收合)", apiBox);
        apiPane.setExpanded(true); // default expanded

        consoleTextArea = new TextArea();
        consoleTextArea.setEditable(false);
        consoleTextArea.setWrapText(true);
        consoleTextArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4;");
        VBox.setVgrow(consoleTextArea, Priority.ALWAYS);

        VBox root = new VBox(controlBox, apiPane, consoleTextArea);

        // Actions
        startButton.setOnAction(e -> startTask());
        stopButton.setOnAction(e -> stopTask());
        sendApiButton.setOnAction(e -> testCustomApi(methodCombo.getValue(), urlField.getText(), bodyArea.getText()));

        // Redirect System.out and System.err
        redirectConsole();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopTask());
        primaryStage.show();
        
        System.out.println("歡迎使用 TronClass 自動點名系統！請點擊上方 [開始掛機] 按鈕。");
    }

    private void startTask() {
        startButton.setDisable(true);
        stopButton.setDisable(false);
        statusLabel.setText("狀態: 運行中...");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #27ae60;");

        backgroundThread = new Thread(() -> {
            try {
                TronScript.startProcess("config.yaml");
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    startButton.setDisable(false);
                    stopButton.setDisable(true);
                    statusLabel.setText("狀態: 已停止");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #c0392b;");
                });
            }
        });
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void stopTask() {
        if (backgroundThread != null && backgroundThread.isAlive()) {
            TronScript.stopProcess();
            System.out.println("正在停止點名監控...");
            statusLabel.setText("狀態: 停止中...");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d35400;");
            stopButton.setDisable(true);
        }
    }

    private void testCustomApi(String method, String url, String body) {
        if (url == null || url.trim().isEmpty()) return;
        Thread t = new Thread(() -> {
            try {
                System.out.println("====== 執行自訂 API 測試 ======");
                System.out.println(method + " " + url);
                
                if (TronScript.currentClient == null) {
                    System.out.println("正在初始化登入...");
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
                    Config config = mapper.readValue(new java.io.File("config.yaml"), Config.class);
                    TronScript.currentClient = new TronClient(config, "log");
                    if (!TronScript.currentClient.login()) {
                        System.out.println("測試失敗: 登入不成功");
                        return;
                    }
                }
                
                String response = TronScript.currentClient.testCustomApi(method, url, body);
                System.out.println(response);
                System.out.println("==============================");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void redirectConsole() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) {
                appendText(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) {
                appendText(new String(b, off, len));
            }
        };

        PrintStream ps = new PrintStream(out, true);
        System.setOut(ps);
        System.setErr(ps);
    }

    private void appendText(String text) {
        Platform.runLater(() -> {
            consoleTextArea.appendText(text);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
