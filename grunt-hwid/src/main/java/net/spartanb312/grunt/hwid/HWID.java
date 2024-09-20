package net.spartanb312.grunt.hwid;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HWID {

    private static final String KEY = "1186118611861186";

    public static void main(String[] args) {
        new Frame(KEY).setVisible(false);
    }

    public static class Frame extends JFrame {
        public Frame(String key) {
            setTitle("HWID Getter");
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLocationRelativeTo(null);
            String hardwareID = "Unknown HWID";
            try {
                String raw = System.getenv("PROCESS_IDENTIFIER")
                        + System.getenv("PROCESSOR_LEVEL")
                        + System.getenv("PROCESSOR_REVISION")
                        + System.getenv("PROCESSOR_ARCHITECTURE")
                        + System.getenv("PROCESSOR_ARCHITEW6432")
                        + System.getenv("NUMBER_OF_PROCESSORS")
                        + System.getenv("COMPUTERNAME");
                String aes = "AES";
                Cipher cipher = Cipher.getInstance(aes);
                SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), aes);
                cipher.init(1, secretKeySpec);
                byte[] result = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
                hardwareID = new String(Base64.getEncoder().encode(result))
                        .replace("/", "s")
                        .replace("=", "e")
                        .replace("+", "p");
            } catch (Exception ignored) {
            }
            copyToClipboard(hardwareID);
            String message = "Your HWID: " + hardwareID + "\n(Copied to clipboard)";
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    "HWID Getter",
                    JOptionPane.PLAIN_MESSAGE,
                    UIManager.getIcon("OptionPane.informationIcon")
            );
        }

        public static void copyToClipboard(String s) {
            StringSelection selection = new StringSelection(s);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        }
    }

}
