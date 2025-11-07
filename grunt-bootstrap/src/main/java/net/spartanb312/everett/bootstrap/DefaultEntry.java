package net.spartanb312.everett.bootstrap;

import javax.swing.*;

public class DefaultEntry {

    public static DefaultEntry INSTANCE = new DefaultEntry();

    public DefaultEntry() {
        JOptionPane.showConfirmDialog(
                null,
                "No entry was specified in MANIFEST Launch-Entry",
                "No entry specified!",
                JOptionPane.YES_NO_OPTION
        );
        System.exit(0);
    }

}
