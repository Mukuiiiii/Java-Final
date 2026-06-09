package com.finalproject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TronScript {

    private static int cnt = 0;
    private static boolean flagDayNight = false;
    private static boolean flagWorking = false;
    public static volatile boolean isRunning = false;
    public static TronClient currentClient = null;

    public static void startProcess(String configPath) {
        isRunning = true;
        cnt = 0;

        File configFile = new File(configPath);
        if (!configFile.exists()) {
            System.err.println("Config file not found: " + configPath);
            System.err.println("Please run with: java -jar tronclass-java.jar <path-to-config.yaml>");
            isRunning = false;
            return;
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Config config;
        try {
            config = mapper.readValue(configFile, Config.class);
        } catch (Exception e) {
            System.err.println("Failed to parse config file: " + e.getMessage());
            e.printStackTrace();
            isRunning = false;
            return;
        }

        Path logPath = Paths.get("log");
        currentClient = new TronClient(config, logPath.toString());

        if (!currentClient.login()) {
            System.err.println("Login sequence failed. Stopping.");
            isRunning = false;
            return;
        }

        int errorCnt = 0;

        while (isRunning) {
            System.out.print(cnt + " ");

            LocalDateTime now = LocalDateTime.now();
            int todayIndex = now.getDayOfWeek() == DayOfWeek.SUNDAY ? 6 : now.getDayOfWeek().getValue() - 1; 
            
            Config.OperatingSchedule schedule = null;
            if (config.operating != null) {
                schedule = config.operating.get(todayIndex);
            }

            if (schedule == null || !schedule.enable) {
                System.out.println("off working day\n");
                sleep(3600);
                continue;
            } else {
                List<String> range = schedule.range;
                LocalTime start = LocalTime.parse(range.get(0), DateTimeFormatter.ofPattern("H:mm"));
                LocalTime end = LocalTime.parse(range.get(1), DateTimeFormatter.ofPattern("H:mm"));
                LocalTime currentTime = now.toLocalTime();

                if (!currentTime.isBefore(start) && !currentTime.isAfter(end)) {
                    if (!flagDayNight) {
                        flagDayNight = true;
                        String text = "starting working...  \n";
                        System.out.println(text);
                        currentClient.mes(text).join();
                    }
                } else {
                    if (flagDayNight) {
                        flagDayNight = false;
                        String text = "sleeping...  \n";
                        System.out.println(text);
                        currentClient.mes(text).join();
                    }
                    System.out.println("off working time\n");
                    sleep(300);
                    continue;
                }
            }

            try {
                currentClient.checkRollcall(cnt);
                if (!flagWorking) {
                    flagWorking = true;
                    String text = "has been restored";
                    System.out.println(text);
                    currentClient.mes(text).join();
                }
            } catch (Exception e) {
                if (flagWorking) {
                    flagWorking = false;
                    String text = "fuck up";
                    System.out.println(text);
                    currentClient.mes(text).join();
                }

                if (errorCnt < config.config.retries) {
                    String text = String.format("check rollcall error on %d  \ntrying %d times  \nerror message: %s", 
                            cnt, errorCnt, e.getMessage());
                    System.out.println(text);
                    currentClient.mes(text).join();
                    errorCnt++;
                } else {
                    break;
                }
            }

            cnt++;
            sleep(config.config.senkaku);
        }
        
        System.out.println("TronClass Task Stopped.");
    }

    public static void stopProcess() {
        isRunning = false;
    }

    private static void sleep(int seconds) {
        try {
            // Check the running flag frequently so we can interrupt quickly
            for (int i = 0; i < seconds && isRunning; i++) {
                Thread.sleep(1000L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
