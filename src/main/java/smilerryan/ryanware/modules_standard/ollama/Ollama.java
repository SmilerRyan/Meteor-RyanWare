package smilerryan.ryanware.modules_standard.ollama;

import java.io.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.List;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import smilerryan.ryanware.RyanWare;

public class Ollama {
    
    private static String escapeJson(String s) {
        if (s == null) {return "";}
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String extractJsonContent(String line) {
        int contentStartIndex = line.indexOf("\"content\":\"");
        if (contentStartIndex == -1) {
            return null;
        }

        contentStartIndex += 11;
        StringBuilder contentBuilder = new StringBuilder();
        boolean isEscaped = false;

        for (int i = contentStartIndex; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (isEscaped) {
                switch (ch) {
                    case '\"': contentBuilder.append('"'); break;
                    case '\\': contentBuilder.append('\\'); break;
                    case 'n': contentBuilder.append('\n'); break;
                    case 'r': contentBuilder.append('\r'); break;
                    case 't': contentBuilder.append('\t'); break;
                    default: contentBuilder.append(ch); break;
                }
                isEscaped = false;
            } else if (ch == '\\') {
                isEscaped = true;
            } else if (ch == '"') {
                break;
            } else {
                contentBuilder.append(ch);
            }
        }

        return contentBuilder.toString();
    }

    private static String cleanThinkingTags(String response) {
        return response
            .replaceAll("(?i)<think>.*?</think>", "")
            .replaceAll("(?i)\\\\u003c/?think\\\\u003e", "")
            .replaceAll("(?i)<think>|</think>", "")
            .trim();
    }

    public static String queryOllama(String baseUrl, String modelName, String prompt, Module specific_module) {
        try {
            URL url = new URL(baseUrl + "/api/chat");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String json = String.format(
                "{\"model\":\"%s\",\"messages\":[{\"role\":\"system\",\"content\":\"%s\"},{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":false}",
                escapeJson(modelName), 
                escapeJson(prompt), 
                escapeJson(prompt)
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                specific_module.error("Ollama query failed: HTTP " + responseCode);
                conn.disconnect();
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                if (!specific_module.isActive()) {
                    conn.disconnect();
                    return "";
                }

                String line;
                while ((line = br.readLine()) != null) {
                    String content = extractJsonContent(line);
                    if (content != null) {
                        response.append(content);
                    }
                }
            }

            conn.disconnect();

            return cleanThinkingTags(response.toString());
            
        } catch (Exception e) {
            specific_module.error("Ollama query failed: " + e.getMessage());
            return null;
        }
    }

}
