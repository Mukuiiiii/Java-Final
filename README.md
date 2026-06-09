# TronClass Auto Rollcall (Java 版)

這是一個基於 Java 與 JavaFX 開發的 TronClass (創課) 自動點名與 API 測試工具。
此程式可以自動登入 TronClass (支援 NTOU CAS 驗證與 Tesseract OCR 驗證碼辨識)，並在背景定期掃描課程以完成自動點名。

## 環境要求
在執行此程式之前，請確保您的電腦已安裝下列軟體：
1. **Java Development Kit (JDK) 17 或以上版本**
2. **Apache Maven 3.9+** (本專案內建了免安裝版的 maven，在 `apache-maven-3.9.6` 資料夾中)
3. **Tesseract-OCR**：
   - 請下載並安裝 [Tesseract-OCR (Windows)](https://github.com/UB-Mannheim/tesseract/wiki)
   - 安裝路徑必須為 `C:\Program Files\Tesseract-OCR\`，否則需要自行修改 `TronClient.java` 內的路徑。

## 安裝與設定方式
1. 將這個專案 Clone 或是下載解壓縮到您的電腦中。
2. 複製 `config.example.yaml` 並將新檔案重新命名為 `config.yaml`。
3. 打開 `config.yaml`，將裡面 `account` 底下的 `user` 與 `passwd` 改為您的學號與密碼。

```yaml
account:
  user: '您的學號'
  passwd: '您的密碼'
```

## 如何運行程式
本程式提供了簡單的啟動腳本，專為 Windows 環境設計：

1. 進入專案資料夾後，直接雙擊執行 `run.bat`。
2. 腳本會自動使用內建的 Maven 進行編譯 (`mvn clean compile`)。
3. 接著會自動跳出 JavaFX 的圖形介面 (GUI)。

### UI 介面操作說明：
- **▶ 開始掛機 (Start)**：點擊後會進行自動登入，並開始背景點名監控。
- **⏹ 停止 (Stop)**：隨時中斷點名監控。
- **自訂 API 測試**：您可以在介面中選擇 GET/POST 方法，填入 API 路徑（例如 `/api/user/recently-visited-courses`），並直接送出請求。程式會自動處理編碼，將結果美化後顯示在下方的日誌區塊。

## 注意事項
- **請勿將您含有帳號密碼的 `config.yaml` Commit 到 Git 倉庫中！** 本專案的 `.gitignore` 已經預設排除了此檔案，請務必確認它不會被上傳。
- 如果編譯過程發生錯誤，請確認您的電腦有正確安裝 JDK 17，並且已將 `java` 指令加入到系統環境變數中。
