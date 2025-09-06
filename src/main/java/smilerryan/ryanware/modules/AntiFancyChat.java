package smilerryan.ryanware.modules;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import smilerryan.ryanware.RyanWare;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import java.util.HashMap;
import java.util.Map;

public class AntiFancyChat extends Module {
    private static final Map<Character, Character> FANCY_TO_NORMAL = new HashMap<>();
    
    static {
        // Mathematical Bold (𝐀-𝐙, 𝐚-𝐳, 𝟎-𝟗)
        char[] boldUpper = "𝐀𝐁𝐂𝐃𝐄𝐅𝐆𝐇𝐈𝐉𝐊𝐋𝐌𝐍𝐎𝐏𝐐𝐑𝐒𝐓𝐔𝐕𝐖𝐗𝐘𝐙".toCharArray();
        char[] boldLower = "𝐚𝐛𝐜𝐝𝐞𝐟𝐠𝐡𝐢𝐣𝐤𝐥𝐦𝐧𝐨𝐩𝐪𝐫𝐬𝐭𝐮𝐯𝐰𝐱𝐲𝐳".toCharArray();
        char[] boldNums = "𝟎𝟏𝟐𝟑𝟒𝟓𝟔𝟕𝟖𝟗".toCharArray();
        
        // Mathematical Italic (𝐴-𝑍, 𝑎-𝑧)
        char[] italicUpper = "𝐴𝐵𝐶𝐷𝐸𝐹𝐺𝐻𝐼𝐽𝐾𝐿𝑀𝑁𝑂𝑃𝑄𝑅𝑆𝑇𝑈𝑉𝑊𝑋𝑌𝑍".toCharArray();
        char[] italicLower = "𝑎𝑏𝑐𝑑𝑒𝑓𝑔ℎ𝑖𝑗𝑘𝑙𝑚𝑛𝑜𝑝𝑞𝑟𝑠𝑡𝑢𝑣𝑤𝑥𝑦𝑧".toCharArray();
        
        // Mathematical Double-Struck (𝔸-ℤ, 𝕒-𝕫, 𝟘-𝟡)
        char[] doubleUpper = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ".toCharArray();
        char[] doubleLower = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕠𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫".toCharArray();
        char[] doubleNums = "𝟘𝟙𝟚𝟛𝟜𝟝𝟞𝟟𝟠𝟡".toCharArray();
        
        // Mathematical Sans-Serif (𝖠-𝖹, 𝖺-𝗓, 𝟢-𝟫)
        char[] sansUpper = "𝖠𝖡𝖢𝖣𝖤𝖥𝖦𝖧𝖨𝖩𝖪𝖫𝖬𝖭𝖮𝖯𝖰𝖱𝖲𝖳𝖴𝖵𝖶𝖷𝖸𝖹".toCharArray();
        char[] sansLower = "𝖺𝖻𝖼𝖽𝖾𝖿𝗀𝗁𝗂𝗃𝗄𝗅𝗆𝗇𝗈𝗉𝗊𝗋𝗌𝗍𝗎𝗏𝗐𝗑𝗒𝗓".toCharArray();
        char[] sansNums = "𝟢𝟣𝟤𝟥𝟦𝟧𝟨𝟩𝟪𝟫".toCharArray();
        
        // Mathematical Monospace (𝙰-𝚉, 𝚊-𝚣, 𝟶-𝟿)
        char[] monoUpper = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉".toCharArray();
        char[] monoLower = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣".toCharArray();
        char[] monoNums = "𝟶𝟷𝟸𝟹𝟺𝟻𝟼𝟽𝟾𝟿".toCharArray();
        
        // Fullwidth characters (Ａ-Ｚ, ａ-ｚ, ０-９)
        char[] fullUpper = "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ".toCharArray();
        char[] fullLower = "ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ".toCharArray();
        char[] fullNums = "０１２３４５６７８９".toCharArray();
        
        // Circled characters (Ⓐ-Ⓩ, ⓐ-ⓩ)
        char[] circleUpper = "ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ".toCharArray();
        char[] circleLower = "ⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ".toCharArray();
        
        // Apply mappings
        mapToNormal(boldUpper, 'A'); mapToNormal(boldLower, 'a'); mapToNormal(boldNums, '0');
        mapToNormal(italicUpper, 'A'); mapToNormal(italicLower, 'a');
        mapToNormal(doubleUpper, 'A'); mapToNormal(doubleLower, 'a'); mapToNormal(doubleNums, '0');
        mapToNormal(sansUpper, 'A'); mapToNormal(sansLower, 'a'); mapToNormal(sansNums, '0');
        mapToNormal(monoUpper, 'A'); mapToNormal(monoLower, 'a'); mapToNormal(monoNums, '0');
        mapToNormal(fullUpper, 'A'); mapToNormal(fullLower, 'a'); mapToNormal(fullNums, '0');
        mapToNormal(circleUpper, 'A'); mapToNormal(circleLower, 'a');
        
        // Superscript numbers
        FANCY_TO_NORMAL.put('⁰', '0'); FANCY_TO_NORMAL.put('¹', '1'); FANCY_TO_NORMAL.put('²', '2');
        FANCY_TO_NORMAL.put('³', '3'); FANCY_TO_NORMAL.put('⁴', '4'); FANCY_TO_NORMAL.put('⁵', '5');
        FANCY_TO_NORMAL.put('⁶', '6'); FANCY_TO_NORMAL.put('⁷', '7'); FANCY_TO_NORMAL.put('⁸', '8');
        FANCY_TO_NORMAL.put('⁹', '9');
        
        // Subscript numbers
        FANCY_TO_NORMAL.put('₀', '0'); FANCY_TO_NORMAL.put('₁', '1'); FANCY_TO_NORMAL.put('₂', '2');
        FANCY_TO_NORMAL.put('₃', '3'); FANCY_TO_NORMAL.put('₄', '4'); FANCY_TO_NORMAL.put('₅', '5');
        FANCY_TO_NORMAL.put('₆', '6'); FANCY_TO_NORMAL.put('₇', '7'); FANCY_TO_NORMAL.put('₈', '8');
        FANCY_TO_NORMAL.put('₉', '9');
        
        // Superscript/subscript letters
        FANCY_TO_NORMAL.put('ᵃ', 'a'); FANCY_TO_NORMAL.put('ᵇ', 'b'); FANCY_TO_NORMAL.put('ᶜ', 'c');
        FANCY_TO_NORMAL.put('ᵈ', 'd'); FANCY_TO_NORMAL.put('ᵉ', 'e'); FANCY_TO_NORMAL.put('ᶠ', 'f');
        FANCY_TO_NORMAL.put('ᵍ', 'g'); FANCY_TO_NORMAL.put('ʰ', 'h'); FANCY_TO_NORMAL.put('ⁱ', 'i');
        FANCY_TO_NORMAL.put('ʲ', 'j'); FANCY_TO_NORMAL.put('ᵏ', 'k'); FANCY_TO_NORMAL.put('ˡ', 'l');
        FANCY_TO_NORMAL.put('ᵐ', 'm'); FANCY_TO_NORMAL.put('ⁿ', 'n'); FANCY_TO_NORMAL.put('ᵒ', 'o');
        FANCY_TO_NORMAL.put('ᵖ', 'p'); FANCY_TO_NORMAL.put('ʳ', 'r'); FANCY_TO_NORMAL.put('ˢ', 's');
        FANCY_TO_NORMAL.put('ᵗ', 't'); FANCY_TO_NORMAL.put('ᵘ', 'u'); FANCY_TO_NORMAL.put('ᵛ', 'v');
        FANCY_TO_NORMAL.put('ʷ', 'w'); FANCY_TO_NORMAL.put('ˣ', 'x'); FANCY_TO_NORMAL.put('ʸ', 'y');
        FANCY_TO_NORMAL.put('ᶻ', 'z');
        
        FANCY_TO_NORMAL.put('ₐ', 'a'); FANCY_TO_NORMAL.put('ₑ', 'e'); FANCY_TO_NORMAL.put('ₒ', 'o');
        FANCY_TO_NORMAL.put('ₓ', 'x'); FANCY_TO_NORMAL.put('ᵤ', 'u');
        
        // Small caps (most common ones)
        FANCY_TO_NORMAL.put('ᴀ', 'a'); FANCY_TO_NORMAL.put('ʙ', 'b'); FANCY_TO_NORMAL.put('ᴄ', 'c');
        FANCY_TO_NORMAL.put('ᴅ', 'd'); FANCY_TO_NORMAL.put('ᴇ', 'e'); FANCY_TO_NORMAL.put('ꜰ', 'f');
        FANCY_TO_NORMAL.put('ɢ', 'g'); FANCY_TO_NORMAL.put('ʜ', 'h'); FANCY_TO_NORMAL.put('ɪ', 'i');
        FANCY_TO_NORMAL.put('ᴊ', 'j'); FANCY_TO_NORMAL.put('ᴋ', 'k'); FANCY_TO_NORMAL.put('ʟ', 'l');
        FANCY_TO_NORMAL.put('ᴍ', 'm'); FANCY_TO_NORMAL.put('ɴ', 'n'); FANCY_TO_NORMAL.put('ᴏ', 'o');
        FANCY_TO_NORMAL.put('ᴘ', 'p'); FANCY_TO_NORMAL.put('ʀ', 'r'); FANCY_TO_NORMAL.put('ꜱ', 's');
        FANCY_TO_NORMAL.put('ᴛ', 't'); FANCY_TO_NORMAL.put('ᴜ', 'u'); FANCY_TO_NORMAL.put('ᴠ', 'v');
        FANCY_TO_NORMAL.put('ᴡ', 'w'); FANCY_TO_NORMAL.put('ʏ', 'y'); FANCY_TO_NORMAL.put('ᴢ', 'z');
    }
    
    private static void mapToNormal(char[] fancyChars, char normalStart) {
        for (int i = 0; i < fancyChars.length; i++) {
            FANCY_TO_NORMAL.put(fancyChars[i], (char)(normalStart + i));
        }
    }

    public AntiFancyChat() {
        super(RyanWare.CATEGORY, RyanWare.modulePrefix_extras + "Anti-FancyChat", "Normalizes fancy Unicode chat text back to plain ASCII, while preserving chat colors.");
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent e) {
        Text original = e.getMessage();
        Text normalized = normalizeFancyText(original);
        if (!normalized.getString().equals(original.getString())) {
            e.setMessage(normalized);
        }
    }

    private Text normalizeFancyText(Text text) {
        String content = text.getString();
        String normalized = normalizeFancy(content);
        
        if (normalized.equals(content)) return text;
        
        MutableText newText = Text.literal(normalized).setStyle(text.getStyle());
        
        for (Text sibling : text.getSiblings()) {
            newText.append(normalizeFancyText(sibling));
        }
        
        return newText;
    }

    private String normalizeFancy(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        StringBuilder out = new StringBuilder(input.length());
        
        for (char c : input.toCharArray()) {
            Character mapped = FANCY_TO_NORMAL.get(c);
            if (mapped != null) {
                out.append(mapped);
            } else {
                out.append(c);
            }
        }
        
        return out.toString();
    }
}