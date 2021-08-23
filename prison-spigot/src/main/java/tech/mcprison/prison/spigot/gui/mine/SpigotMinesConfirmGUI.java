package tech.mcprison.prison.spigot.gui.mine;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.entity.Player;
import tech.mcprison.prison.spigot.gui.guiutility.Button;
import tech.mcprison.prison.spigot.gui.guiutility.ButtonLore;
import tech.mcprison.prison.spigot.gui.guiutility.PrisonGUI;
import tech.mcprison.prison.spigot.gui.guiutility.SpigotGUIComponents;

/**
 * @author GABRYCA
 */
public class SpigotMinesConfirmGUI extends SpigotGUIComponents {

    private final Player p;
    private final String mineName;

    public SpigotMinesConfirmGUI(Player p, String mineName) {
        this.p = p;
        this.mineName = mineName;
    }

    public void open(){

        // Create GUI.
        int dimension = 9;
        PrisonGUI gui = new PrisonGUI(p, dimension, "&3Mines -> Delete");

        ButtonLore confirmLore = new ButtonLore(messages.getString("Lore.ClickToConfirm"), null);
        ButtonLore cancelLore = new ButtonLore(messages.getString("Lore.ClickToCancel"), null);


        // Position of the button
        gui.addButton(new Button(2, XMaterial.EMERALD_BLOCK, confirmLore, "&3" + "Confirm: " + mineName));
        gui.addButton(new Button(6, XMaterial.REDSTONE_BLOCK, cancelLore, "&3" + "Cancel: " + mineName));

        // Open GUI.
        gui.open();
    }
}
