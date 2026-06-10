package com.finalproject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TronClient {

    private static final String TRON = "https://tronclass.ntou.edu.tw";
    private static final Pattern PATTERN = Pattern.compile("(LT[^\"]+)");

    private Config config;
    private HttpClient httpClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Path logPath;

    public TronClient(Config config, String logDir) {
        this.config = config;
        this.logPath = Paths.get(logDir);
        initHttpClient();
    }

    private String currentUserAgent;
    private CookieManager cookieManager;

    private void initHttpClient() {
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(this.cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        List<String> userAgents = Arrays.asList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edge/136.0.0.0",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.5410.0 Safari/537.36",
                "Mozilla/5.0 (Android 10; Mobile; rv:78.0) Gecko/20100101 Firefox/78.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:83.0) Gecko/20100101 Firefox/83.0");
        this.currentUserAgent = userAgents.get(new Random().nextInt(userAgents.size()));
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", this.currentUserAgent)
                .header("Accept", "*/*");
    }

    public void log(Path path, String request, int statusCode, String body, int cnt) {
        if (!config.config.enableLog)
            return;
        try {
            Files.createDirectories(path.getParent());
            Map<String, Object> data = new HashMap<>();
            data.put("request", request);
            data.put("status_code", statusCode);
            try {
                data.put("body", jsonMapper.readTree(body));
            } catch (Exception e) {
                data.put("body", body);
            }

            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String out = time + " | " + cnt + "\n" +
                    jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n";

            Files.writeString(path, out, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> mes(String text) {
        String messageText = config.account.user + "  \n" + text;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (config.notifications.tg != null && config.notifications.tg.enable) {
            Map<String, String> data = new HashMap<>();
            data.put("chat_id", config.notifications.tg.chat);
            data.put("text", messageText);

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.telegram.org/" + config.notifications.tg.key + "/sendMessage"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(data)))
                        .build();
                futures.add(httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenApply(r -> null));
            } catch (Exception ignored) {
            }
        }

        if (config.notifications.dc != null && config.notifications.dc.enable) {
            Map<String, String> data = new HashMap<>();
            data.put("content", messageText);

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://discord.com/api/v10/channels/" + config.notifications.dc.chat + "/messages"))
                        .header("Authorization", "Bot " + config.notifications.dc.key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(data)))
                        .build();
                futures.add(httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding()).thenApply(r -> null));
            } catch (Exception ignored) {
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public boolean login() {
        for (int attempt = 0; attempt < config.config.retries; attempt++) {
            try {
                List<String> userAgents = Arrays.asList(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edge/136.0.0.0",
                        "Mozilla/5.0 (Series40; Nokia3310/11.95; Profile/MIDP-5.0 Configuration/CLDC-1.1) Gecko/537.36 S40OviBrowser/5.0.0.0.0",
                        "Mozilla/5.0 (Android 10; Mobile; rv:78.0) Gecko/20100101 Firefox/78.0",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:83.0) Gecko/20100101 Firefox/83.0");
                this.currentUserAgent = userAgents.get(new Random().nextInt(userAgents.size()));

                System.out.println("Attempting login for user: " + config.account.user + " with password: "
                        + config.account.passwd);

                // 1. Get LT & Initial Cookies using Jsoup
                org.jsoup.Connection.Response loginForm = org.jsoup.Jsoup.connect(TRON + "/login?next=/user/index")
                        .userAgent(currentUserAgent)
                        .method(org.jsoup.Connection.Method.GET)
                        .execute();

                String lt = loginForm.parse().select("input[name=lt]").val();
                if (lt == null || lt.isEmpty())
                    throw new Exception("LT token not found in login page");

                java.util.Map<String, String> cookies = new java.util.HashMap<>(loginForm.cookies());

                // 2. Get Captcha
                org.jsoup.Connection.Response captchaResp = org.jsoup.Jsoup
                        .connect("https://tccas.ntou.edu.tw/cas/captcha.jpg")
                        .userAgent(currentUserAgent)
                        .cookies(cookies)
                        .ignoreContentType(true)
                        .execute();

                cookies.putAll(captchaResp.cookies());

                BufferedImage image = ImageIO.read(new ByteArrayInputStream(captchaResp.bodyAsBytes()));
                BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(),
                        BufferedImage.TYPE_BYTE_GRAY);
                grayImage.getGraphics().drawImage(image, 0, 0, null);

                ITesseract instance = new Tesseract();

                String ocrPath = config.config.ocrPath;
                if (ocrPath == null || ocrPath.trim().isEmpty()) {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        ocrPath = "C:\\Program Files\\Tesseract-OCR\\tessdata";
                    } else if (os.contains("mac")) {
                        ocrPath = "/opt/homebrew/share/tessdata";
                    } else {
                        ocrPath = "/usr/share/tesseract-ocr/4.00/tessdata";
                    }
                }

                instance.setDatapath(ocrPath);
                instance.setTessVariable("tessedit_char_whitelist", "0123456789");
                instance.setPageSegMode(8); // psm 8

                String text = instance.doOCR(grayImage);
                String cap = text.replaceAll("[^0-9]", "");

                System.out.println("Guessed Captcha: " + cap + " | Post URI: " + loginForm.url().toString());

                // 3. Post Login using Jsoup
                org.jsoup.Connection.Response loginSubmit = org.jsoup.Jsoup.connect(loginForm.url().toString())
                        .userAgent(currentUserAgent)
                        .cookies(cookies)
                        .data("username", config.account.user)
                        .data("password", config.account.passwd)
                        .data("captcha", cap)
                        .data("lt", lt)
                        .data("execution", "e1s1")
                        .data("_eventId", "submit")
                        .data("submit", "登錄")
                        .method(org.jsoup.Connection.Method.POST)
                        .followRedirects(true)
                        .execute();

                if (loginSubmit.body().contains("forget-password")) {
                    System.out.println("Login failed response length: " + loginSubmit.body().length());
                    throw new Exception("Login failed! Incorrect captcha or credentials.");
                }

                cookies.putAll(loginSubmit.cookies());
                System.out.println("Login Success!");

                // Rebuild Java 11 HttpClient with these successful cookies
                this.cookieManager = new CookieManager();
                this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
                for (java.util.Map.Entry<String, String> entry : cookies.entrySet()) {
                    java.net.HttpCookie cookie = new java.net.HttpCookie(entry.getKey(), entry.getValue());
                    cookie.setDomain("ntou.edu.tw");
                    cookie.setPath("/");
                    this.cookieManager.getCookieStore().add(URI.create("https://tronclass.ntou.edu.tw/"), cookie);
                }

                this.httpClient = HttpClient.newBuilder()
                        .cookieHandler(this.cookieManager)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                return true;

            } catch (Exception e) {
                System.err.println("login | retry attempt " + attempt + " error: " + e.getMessage());
            }
        }
        System.err.println("Max retries reached! login failed\nusername or password may be incorrect\ncheck password!");
        return false;
    }

    public int checkRollcall(int cnt) {
        try {
            HttpRequest req = requestBuilder(TRON + "/api/radar/rollcalls?api_version=1.1.0").GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            LocalDateTime today = LocalDateTime.now();
            String y = String.valueOf(today.getYear());
            String m = String.valueOf(today.getMonthValue());
            String d = String.valueOf(today.getDayOfMonth());
            log(logPath.resolve(y).resolve(m).resolve(d + ".log"), resp.uri().toString(), resp.statusCode(),
                    resp.body(), cnt);

            JsonNode root = jsonMapper.readTree(resp.body());
            JsonNode rollcalls = root.get("rollcalls");

            if (rollcalls != null && rollcalls.isArray() && rollcalls.size() > 0) {
                JsonNode rollcall = rollcalls.get(0);
                String statusStr = rollcall.has("status") ? rollcall.get("status").asText() : "";
                int id = rollcall.has("rollcall_id") ? rollcall.get("rollcall_id").asInt() : 0;

                if ("on_call_fine".equals(statusStr)) {
                    System.out.println("rollcalled");
                    return 0;
                } else if (rollcall.has("is_number") && rollcall.get("is_number").asBoolean()) {
                    String text = "start num\n  id:" + id;
                    System.out.println(text);
                    mes(text).join();
                    number(id);
                    return 1;
                } else if (rollcall.has("is_radar") && rollcall.get("is_radar").asBoolean()) {
                    String text = "start radar\n  id:" + id;
                    System.out.println(text);
                    mes(text).join();
                    radar(id);
                    return 2;
                } else {
                    System.out.println("maybe qrcode");
                    return 3;
                }
            } else {
                System.out.println("not call");
                return -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
    }

    private String randomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public String testRecentCourses() {
        try {
            HttpRequest req = requestBuilder(TRON + "/api/user/recently-visited-courses").GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public String testCustomApi(String method, String urlPath, String body) {
        try {
            String fullUrl = urlPath.startsWith("http") ? urlPath
                    : TRON + (urlPath.startsWith("/") ? urlPath : "/" + urlPath);
            HttpRequest.Builder builder = requestBuilder(fullUrl);

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (body != null && !body.trim().isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(body);
                builder.header("Content-Type", "application/json");
            }

            switch (method.toUpperCase()) {
                case "POST":
                    builder.POST(bodyPublisher);
                    break;
                case "PUT":
                    builder.PUT(bodyPublisher);
                    break;
                case "DELETE":
                    builder.DELETE();
                    break;
                case "GET":
                default:
                    builder.GET();
                    break;
            }

            HttpRequest req = builder.build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            String responseBody = resp.body();
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Object json = mapper.readValue(responseBody, Object.class);
                responseBody = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            } catch (Exception parseEx) {
                // Not JSON, just use raw string
            }

            return "HTTP Status: " + resp.statusCode() + "\nBody:\n" + responseBody;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    public void radar(int rcid) {
        try {
            HttpRequest req = requestBuilder(TRON + "/api/rollcall/" + rcid + "/answer")
                    .PUT(HttpRequest.BodyPublishers.noBody()).build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void number(int rcid) {
        AtomicInteger succeed = new AtomicInteger(0);
        AtomicBoolean ralled = new AtomicBoolean(false);
        Semaphore semaphore = new Semaphore(2000);
        String device = randomId();
        AtomicReference<String> finalCode = new AtomicReference<>("NA");

        ExecutorService executor = Executors.newFixedThreadPool(200); // Thread pool to control max concurrent native
                                                                      // threads
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        long startTime = System.nanoTime();

        for (int i = 0; i < 10000; i++) {
            final int tryCode = i;
            if (ralled.get())
                break;

            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                if (ralled.get())
                    return;
                try {
                    semaphore.acquire();
                    String codeStr = String.format("%04d", tryCode);

                    Map<String, String> jsonBody = new HashMap<>();
                    jsonBody.put("deviceId", device);
                    jsonBody.put("numberCode", codeStr);

                    HttpRequest req = requestBuilder(TRON + "/api/rollcall/" + rcid + "/answer_number_rollcall")
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(jsonBody)))
                            .build();

                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

                    if (resp.statusCode() == 200) {
                        finalCode.set(codeStr);
                        ralled.set(true); // Stop others
                        System.out.println(codeStr);
                        mes(codeStr).join();
                    }

                    succeed.incrementAndGet();
                } catch (Exception e) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {
                    }
                } finally {
                    semaphore.release();
                }
            }, executor);

            tasks.add(task);
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        long endTime = System.nanoTime();
        double timeDiff = (endTime - startTime) / 1_000_000_000.0;

        String text = String.format("Total time: %.2f\nTotal request: %d/10000\nCode: %s\n",
                timeDiff, succeed.get(), finalCode.get());
        System.out.println(text);
        mes(text).join();
    }
}
