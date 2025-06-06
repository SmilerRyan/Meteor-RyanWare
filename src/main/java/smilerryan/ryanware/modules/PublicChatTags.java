package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import java.util.*;

public class PublicChatTags extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> serverTagStrings = sgGeneral.add(new StringListSetting.Builder()
        .name("server-tags")
        .description("List of server tags (ip|prefix|suffix), | and \\ escaped.")
        .defaultValue(Collections.emptyList())
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("If enabled, messages/commands will not be sent, only IP and adjusted message will be logged.")
        .defaultValue(false)
        .build()
    );

    public PublicChatTags() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "chat-tags", "Adds a prefix and suffix to public messages per server.");
    }

    private String getServerIp() {
        if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address.toLowerCase();
        } else if (mc.isInSingleplayer()) {
            return "singleplayer";
        } else {
            return "unknown";
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        String ip = getServerIp();
        boolean edited = false;
        String modified = event.message;
    
        for (ServerTag tag : getServerTags()) {
            if (ip.equalsIgnoreCase(tag.ip)) {
                modified = tag.prefix + event.message + tag.suffix;
                edited = true;
                break;
            }
        }
    
        if (debugMode.get()) {
            info(ip + ": " + (edited ? modified : event.message));
            event.cancel();
            return;
        }
    
        if (edited) {
            if (modified.startsWith("/")) {
                event.cancel();
                String rawCommand = modified.substring(1);
                mc.player.networkHandler.sendChatCommand(rawCommand);
            } else {
                event.message = modified;
            }
        }
    }
    

    // Parse List<String> to List<ServerTag>
    private List<ServerTag> getServerTags() {
        List<ServerTag> list = new ArrayList<>();
        for (String s : serverTagStrings.get()) {
            list.add(ServerTag.fromString(s));
        }
        return list;
    }

    // Serialize List<ServerTag> back to List<String>
    private void setServerTags(List<ServerTag> tags) {
        List<String> strings = new ArrayList<>();
        for (ServerTag tag : tags) {
            strings.add(tag.toString());
        }
        serverTagStrings.set(strings);
    }

    // Add current server with empty prefix/suffix if not present
    public void addCurrentServerIfMissing() {
        String ip = getServerIp();
        List<ServerTag> tags = getServerTags();

        for (ServerTag tag : tags) {
            if (tag.ip.equalsIgnoreCase(ip)) return; // already exists
        }

        tags.add(new ServerTag(ip, "", ""));
        setServerTags(tags);
    }

    // Remove tag by IP
    public void removeServerTag(String ip) {
        List<ServerTag> tags = getServerTags();
        tags.removeIf(t -> t.ip.equalsIgnoreCase(ip));
        setServerTags(tags);
    }

    // Update prefix/suffix for an IP
    public void updateServerTag(String ip, String prefix, String suffix) {
        List<ServerTag> tags = getServerTags();
        for (ServerTag tag : tags) {
            if (tag.ip.equalsIgnoreCase(ip)) {
                tag.prefix = prefix;
                tag.suffix = suffix;
                setServerTags(tags);
                return;
            }
        }
        // Not found, add new
        tags.add(new ServerTag(ip, prefix, suffix));
        setServerTags(tags);
    }

    private static class ServerTag {
        public String ip;
        public String prefix;
        public String suffix;

        public ServerTag(String ip, String prefix, String suffix) {
            this.ip = ip;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        @Override
        public String toString() {
            return escape(ip) + "|" + escape(prefix) + "|" + escape(suffix);
        }

        public static ServerTag fromString(String s) {
            String[] parts = s.split("(?<!\\\\)\\|", 3);
            if (parts.length < 3) return new ServerTag("unknown", "", "");
            return new ServerTag(unescape(parts[0]), unescape(parts[1]), unescape(parts[2]));
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("|", "\\|");
        }

        private static String unescape(String s) {
            return s.replace("\\|", "|").replace("\\\\", "\\");
        }
    }
}
