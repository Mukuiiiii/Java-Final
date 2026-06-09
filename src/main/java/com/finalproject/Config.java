package com.finalproject;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class Config {
    public Account account;
    public Notifications notifications;
    public AppConfig config;
    public Map<Integer, OperatingSchedule> operating;

    public static class Account {
        public String user;
        public String passwd;
    }

    public static class Notifications {
        public Telegram tg;
        public Discord dc;
    }

    public static class Telegram {
        public boolean enable;
        public String key;
        public String chat;
    }

    public static class Discord {
        public boolean enable;
        public String key;
        public String chat;
    }

    public static class AppConfig {
        @JsonProperty("enable_log")
        public boolean enableLog;
        @JsonProperty("Senkaku")
        public int senkaku;
        public int retries;
        @JsonProperty("ocr_path")
        public String ocrPath;
    }

    public static class OperatingSchedule {
        public boolean enable;
        public List<String> range;
    }
}
