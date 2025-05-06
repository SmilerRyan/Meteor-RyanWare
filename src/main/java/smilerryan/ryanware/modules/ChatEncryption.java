package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;

import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatEncryption extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enables chat encryption.")
        .defaultValue(true)
        .build()
    );

    private final Setting<EncryptionMode> encryptionMode = sgGeneral.add(new EnumSetting.Builder<EncryptionMode>()
        .name("mode")
        .description("Encryption method.")
        .defaultValue(EncryptionMode.NO_ENCRYPTION)
        .build()
    );

    private final Setting<Integer> caesarOffset = sgGeneral.add(new IntSetting.Builder()
        .name("caesar-offset")
        .description("Caesar cipher offset (used only in Caesar mode).")
        .defaultValue(5)
        .min(1)
        .sliderMax(25)
        .visible(() -> encryptionMode.get() == EncryptionMode.CAESAR)
        .build()
    );

    private final Setting<String> aesPassphrase = sgGeneral.add(new StringSetting.Builder()
        .name("aes-passphrase")
        .description("AES encryption passphrase.")
        .defaultValue("secret")
        .visible(() -> encryptionMode.get() == EncryptionMode.AES)
        .build()
    );

    private final Setting<String> prefix = sgGeneral.add(new StringSetting.Builder()
        .name("prefix")
        .description("Prefix to identify encrypted messages.")
        .defaultValue("[E][")
        .build()
    );

    private final Setting<String> suffix = sgGeneral.add(new StringSetting.Builder()
        .name("suffix")
        .description("Suffix to identify encrypted messages.")
        .defaultValue("]")
        .build()
    );

    public enum EncryptionMode {
        NO_ENCRYPTION("No Encryption"),
        CAESAR("Caesar (Meme)"),
        ROT13("Rot13 (Meme)"),
        AES("AES (Serious)");

        private final String label;

        EncryptionMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public ChatEncryption() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix + "chat-encryption", "Encrypts non-command chat and auto-decrypts embedded encrypted messages.");
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (!enabled.get() || event.message.startsWith("/")) return;

        // Extract the original style from the message
        String encryptedMessage = switch (encryptionMode.get()) {
            case NO_ENCRYPTION -> base64Encrypt(event.message);
            case CAESAR -> base64Encrypt(caesarEncrypt(event.message, caesarOffset.get()));
            case ROT13 -> base64Encrypt(rot13(event.message));
            case AES -> aesEncrypt(event.message, aesPassphrase.get());
        };

        // Wrap the encrypted message with the prefix and suffix
        event.message = prefix.get() + encryptedMessage + suffix.get();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!enabled.get()) return;

        String raw = event.getMessage().getString();
        String prefixValue = Pattern.quote(prefix.get());
        String suffixValue = Pattern.quote(suffix.get());
        Pattern pattern = Pattern.compile(prefixValue + "([A-Za-z0-9+/=\\-:]+)" + suffixValue); // Match encrypted content between prefix and suffix

        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) return;

        net.minecraft.text.MutableText full = Text.literal("");
        int lastEnd = 0;
        matcher.reset();

        while (matcher.find()) {
            String before = raw.substring(lastEnd, matcher.start());
            if (!before.isEmpty()) full.append(Text.literal(before).styled(style -> style)); // Append the non-encrypted text

            String encrypted = matcher.group(1);

            // Decrypt the message based on selected mode
            String decrypted = switch (encryptionMode.get()) {
                case NO_ENCRYPTION -> base64Decrypt(encrypted);
                case CAESAR -> caesarDecrypt(base64Decrypt(encrypted), caesarOffset.get());
                case ROT13 -> rot13(base64Decrypt(encrypted));
                case AES -> aesDecrypt(encrypted, aesPassphrase.get());
            };

            // Apply the original style to the decrypted message
            full.append(Text.literal(decrypted).styled(style -> style.withColor(Style.EMPTY.getColor()))); // Modify as needed to keep the color or other styles

            lastEnd = matcher.end();
        }

        if (lastEnd < raw.length()) {
            full.append(Text.literal(raw.substring(lastEnd))); // Append any remaining non-encrypted text
        }

        event.setMessage(full);
    }

    private String base64Encrypt(String message) {
        return Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Decrypt(String encrypted) {
        try {
            return new String(Base64.getDecoder().decode(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "[Decode Error]";
        }
    }

    private String caesarEncrypt(String message, int offset) {
        StringBuilder result = new StringBuilder();
        for (char c : message.toCharArray()) {
            result.append((char) ((c + offset) % 65536)); // Use 65536 to handle all possible Unicode characters
        }
        return result.toString();
    }

    private String caesarDecrypt(String message, int offset) {
        return caesarEncrypt(message, -offset); // Invert the offset to decrypt
    }

    private String rot13(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 'a' && c <= 'z') sb.append((char) ((c - 'a' + 13) % 26 + 'a'));
            else if (c >= 'A' && c <= 'Z') sb.append((char) ((c - 'A' + 13) % 26 + 'A'));
            else if (c >= '0' && c <= '9') sb.append((char) ((c - '0' + 5) % 10 + '0')); // Rotate numbers by 5
            else sb.append(c);
        }
        return sb.toString();
    }

    private String aesEncrypt(String plaintext, String passphrase) {
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveAESKey(passphrase), ivSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            return "[AES Encode Error]";
        }
    }

    private String aesDecrypt(String encrypted, String passphrase) {
        try {
            String[] parts = encrypted.split(":");
            if (parts.length != 2) return "[AES Format Error]";

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, deriveAESKey(passphrase), ivSpec);

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[AES Decode Error]";
        }
    }

    private SecretKeySpec deriveAESKey(String passphrase) throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

}
