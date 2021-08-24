package tech.mcprison.prison.spigot.commands;

import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;

import com.cryptomorin.xseries.XMaterial;

import org.bukkit.inventory.ItemStack;
import tech.mcprison.prison.Prison;
import tech.mcprison.prison.PrisonAPI;
import tech.mcprison.prison.commands.Arg;
import tech.mcprison.prison.commands.Command;
import tech.mcprison.prison.commands.Wildcard;
import tech.mcprison.prison.integration.EconomyCurrencyIntegration;
import tech.mcprison.prison.internal.CommandSender;
import tech.mcprison.prison.output.Output;
import tech.mcprison.prison.spigot.SpigotPlatform;
import tech.mcprison.prison.spigot.SpigotPrison;
import tech.mcprison.prison.spigot.compat.Compatibility;
import tech.mcprison.prison.spigot.game.SpigotPlayer;
import tech.mcprison.prison.spigot.sellall.SellAllBlockData;
import tech.mcprison.prison.spigot.sellall.SellAllUtil;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author GABRYCA
 * @author RoyalBlueRanger (rBluer)
 */
public class PrisonSpigotSellAllCommands extends PrisonSpigotBaseCommands {

    private static PrisonSpigotSellAllCommands instance;
    private final Configuration messages = SpigotPrison.getInstance().getMessagesConfig();
    private final Compatibility compat = SpigotPrison.getInstance().getCompatibility();

    /**
     * Check if SellAll's enabled.
     * */
    public static boolean isEnabled(){
        return SpigotPrison.getInstance().getConfig().getString("sellall").equalsIgnoreCase("true");
    }

    /**
     * Get SellAll Commands instance.
     * */
    public static PrisonSpigotSellAllCommands get() {
        if (instance == null && isEnabled()) {
            instance = new PrisonSpigotSellAllCommands();
        }
        if (instance == null){
            return null;
        }
        return instance;
    }

    @Command(identifier = "sellall set currency", description = "SellAll set currency command", onlyPlayers = false, permissions = "prison.sellall.currency")
    private void sellAllCurrency(CommandSender sender,
                                 @Arg(name = "currency", description = "Currency name.", def = "default") @Wildcard String currency){

        EconomyCurrencyIntegration currencyEcon = PrisonAPI.getIntegrationManager().getEconomyForCurrency(currency);
        if (currencyEcon == null && !currency.equalsIgnoreCase("default")) {
            Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllCurrencyNotFound")), currency);
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.setCurrency(currency)){
            Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllCurrencyEditedSuccess") + " [" + currency + "]"));
        }
    }

    @Command(identifier = "sellall", description = "SellAll main command", onlyPlayers = false)
    private void sellAllCommands(CommandSender sender) {

        if (!isEnabled()) return;

        if (sender.hasPermission("prison.admin")) {
            String registeredCmd = Prison.get().getCommandHandler().findRegisteredCommand( "sellall help" );
            sender.dispatchCommand(registeredCmd);
        } else {
            String registeredCmd = Prison.get().getCommandHandler().findRegisteredCommand( "sellall sell" );
            sender.dispatchCommand(registeredCmd);
        }
    }

    @Command(identifier = "sellall delay", description = "SellAll delay.", onlyPlayers = false, permissions = "prison.sellall.delay")
    private void sellAllDelay(CommandSender sender,
                              @Arg(name = "boolean", description = "True to enable or false to disable.", def = "null") String enable){

        if (!isEnabled()) return;

        if (!(enable.equalsIgnoreCase("true") || enable.equalsIgnoreCase("false"))){
            Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.InvalidBooleanInput")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();

        if (sellAllUtil == null){
            return;
        }

        boolean enableBoolean = getBoolean(enable);
        if (sellAllUtil.isSellAllDelayEnabled == enableBoolean){
            if (enableBoolean){
                Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllDelayAlreadyEnabled")));
            } else {
                Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllDelayAlreadyDisabled")));
            }
            return;
        }

        if (sellAllUtil.setDelayEnable(enableBoolean)){
            if (enableBoolean){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllDelayEnabled")));
            } else {
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllDelayDisabled")));
            }
        }
    }

    @Command(identifier = "sellall set delay", description = "Edit SellAll delay.", onlyPlayers = false, permissions = "prison.sellall.delay")
    private void sellAllDelaySet(CommandSender sender,
                                 @Arg(name = "delay", description = "Set delay value in seconds.", def = "0") String delay){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        int delayValue;
        try {
            delayValue = Integer.parseInt(delay);
        } catch (NumberFormatException ex){
            Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllDelayNotNumber")));
            return;
        }

        if (sellAllUtil.setDelay(delayValue)){
            Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllDelayEditedWithSuccess") + " [" + delayValue + "s]"));
        }
    }

    @Command(identifier = "sellall autosell", description = "Enable SellAll AutoSell.", onlyPlayers = false, permissions = "prison.autosell.edit")
    private void sellAllAutoSell(CommandSender sender,
                                 @Arg(name = "boolean", description = "True to enable or false to disable.", def = "null") String enable){

        if (!isEnabled()) return;

        if (enable.equalsIgnoreCase("perusertoggleable")){
            sellAllAutoSellPerUserToggleable(sender, enable);
            return;
        }

        if (!(enable.equalsIgnoreCase("true") || enable.equalsIgnoreCase("false"))){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.InvalidBooleanInput")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        boolean enableBoolean = getBoolean(enable);
        if (sellAllUtil.isAutoSellEnabled == enableBoolean){
            if (enableBoolean){
                Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoSellAlreadyEnabled")));
            } else {
                Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoSellAlreadyDisabled")));
            }
            return;
        }

        if (sellAllUtil.setAutoSell(enableBoolean)){
            if (enableBoolean){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoSellEnabled")));
            } else {
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoSellDisabled")));
            }
        }
    }

    @Command(identifier = "sellall autosell perUserToggleable", description = "Enable AutoSell perUserToggleable.", onlyPlayers = false, permissions = "prison.autosell.edit")
    private void sellAllAutoSellPerUserToggleable(CommandSender sender,
                                                  @Arg(name = "boolean", description = "True to enable or false to disable", def = "null") String enable){

        if (!isEnabled()) return;

        if (!(enable.equalsIgnoreCase("true") || enable.equalsIgnoreCase("false"))){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.InvalidBooleanInput")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        boolean enableBoolean = getBoolean(enable);
        if (sellAllUtil.isAutoSellPerUserToggleable == enableBoolean){
            if (enableBoolean){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoPerUserToggleableAlreadyEnabled")));
            } else {
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoPerUserToggleableAlreadyDisabled")));
            }
            return;
        }

        if (sellAllUtil.setAutoSellPerUserToggleable(enableBoolean)){
            if (enableBoolean){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoPerUserToggleableEnabled")));
            } else {
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllAutoPerUserToggleableDisabled")));
            }
        }
    }

    @Command(identifier = "sellall sell", description = "SellAll sell command.", onlyPlayers = true)
    public void sellAllSellCommand(CommandSender sender,
                                   @Arg(name = "notification", def="",
                                           description = "Notification about the sellall transaction. Defaults to normal. " +
                                                   "'silent' suppresses results. [silent]") String notification ){

        if (!isEnabled()) return;

        Player p = getSpigotPlayer(sender);

        if (p == null){
            Output.get().sendInfo(sender, SpigotPrison.format("&cSorry but you can't use that from the console!"));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.isPlayerInDisabledWorld(p)) return;

        if (sellAllUtil.isSellAllSellPermissionEnabled){
            String permission = sellAllUtil.permissionSellAllSell;
            if (permission == null || !p.hasPermission(permission)){
                Output.get().sendWarn(new SpigotPlayer(p), SpigotPrison.format(messages.getString("Message.SellAllMissingPermission") + " [" + permission + "]"));
                return;
            }
        }

        boolean notifications = (notification != null && "silent".equalsIgnoreCase( notification ));

        sellAllUtil.sellAllSell(p, false, notifications, true, true, false, true);
    }

    @Command(identifier = "sellall hand", description = "Sell only what is in your hand if sellable.", onlyPlayers = true)
    public void sellAllSellHandCommand(CommandSender sender){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();

        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.isSellAllHandEnabled){
            Output.get().sendWarn(sender, "The command /sellall hand is disabled from the config!");
            return;
        }

        Player p = getSpigotPlayer(sender);

        if (p == null){
            Output.get().sendInfo(sender, SpigotPrison.format("&cSorry but you can't use that from the console!"));
            return;
        }

        if (sellAllUtil.isPlayerInDisabledWorld(p)) return;

        if (sellAllUtil.isSellAllSellPermissionEnabled){
            String permission = sellAllUtil.permissionSellAllSell;
            if (permission == null || !p.hasPermission(permission)){
                Output.get().sendWarn(new SpigotPlayer(p), SpigotPrison.format(messages.getString("Message.SellAllMissingPermission") + " [" + permission + "]"));
                return;
            }
        }

        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        itemStacks.add(compat.getItemInMainHand(p));

        sellAllUtil.sellAllSell(p, itemStacks, false, false, true, true, false, true, true);
    }

    public void sellAllSell(Player p){
        if (!isEnabled()) return;

        if (p == null){
            Output.get().sendInfo(new SpigotPlayer(p), SpigotPrison.format("&cSorry but you can't use that from the console!"));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.isPlayerInDisabledWorld(p)) return;

        if (sellAllUtil.isSellAllSellPermissionEnabled){
            String permission = sellAllUtil.permissionSellAllSell;
            if (permission == null || !p.hasPermission(permission)){
                Output.get().sendWarn(new SpigotPlayer(p), SpigotPrison.format(messages.getString("Message.SellAllMissingPermission") + " [" + permission + "]"));
                return;
            }
        }

        sellAllUtil.sellAllSell(p, true, false, true, true, false, true);
    }

    @Command(identifier = "sellall delaysell", description = "Like SellAll Sell command but this will be delayed for some " +
            "seconds and if sellall sell commands are triggered during this delay, " +
            "they will sum up to the total value that will be visible in a notification at the end of the delay. " +
            "Running more of these commands before a delay have been completed won't reset it and will do the same of /sellall sell " +
            "without a notification (silently).", onlyPlayers = true)
    public void sellAllSellWithDelayCommand(CommandSender sender){

        if (!isEnabled()) return;

        Player p = getSpigotPlayer(sender);

        if (p == null){
            Output.get().sendInfo(sender, SpigotPrison.format("&cSorry but you can't use that from the console!"));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.isPlayerInDisabledWorld(p)) return;

        if (sellAllUtil.isSellAllSellPermissionEnabled){
            String permission = sellAllUtil.permissionSellAllSell;
            if (permission == null || !p.hasPermission(permission)){
                Output.get().sendWarn(new SpigotPlayer(p), SpigotPrison.format(messages.getString("Message.SellAllMissingPermission") + " [" + permission + "]"));
                return;
            }
        }

        if (!sellAllUtil.isAutoSellEarningNotificationDelayEnabled){
            sellAllSellCommand(sender, "silent");
            return;
        }

        sellAllUtil.sellAllSell(p, false, false, false, false, true, false);
    }


    @Command(identifier = "sellall auto toggle", description = "Let the user enable or disable sellall auto", onlyPlayers = true)
    private void sellAllAutoEnableUser(CommandSender sender){

        if (!isEnabled()) return;

        Player p = getSpigotPlayer(sender);

        // Sender must be a Player, not something else like the Console.
        if (p == null) {
            Output.get().sendError(sender, SpigotPrison.format(getMessages().getString("Message.CantRunGUIFromConsole")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.isPlayerInDisabledWorld(p)) return;

        if (!sellAllUtil.isAutoSellPerUserToggleable){
            return;
        }

        String permission = sellAllUtil.permissionAutoSellPerUserToggleable;
        if (sellAllUtil.isAutoSellPerUserToggleablePermEnabled && (permission != null && !p.hasPermission(permission))){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllMissingPermissionToToggleAutoSell") + " [" + permission + "]"));
            return;
        }

        if (sellAllUtil.setAutoSellPlayer(p, !sellAllUtil.isPlayerAutoSellEnabled(p))){
            if (sellAllUtil.isPlayerAutoSellEnabled(p)){
                Output.get().sendInfo(new SpigotPlayer(p), SpigotPrison.format(messages.getString("Message.SellAllAutoEnabled")));
            } else {
                Output.get().sendInfo(new SpigotPlayer(p), SpigotPrison.format(messages.getString("Message.SellAllAutoDisabled")));
            }
        }
    }

    @Command(identifier = "sellall gui", description = "SellAll GUI command", altPermissions = "prison.admin", onlyPlayers = true)
    private void sellAllGuiCommand(CommandSender sender){

        if (!isEnabled()) return;

        Player p = getSpigotPlayer(sender);

        // Sender must be a Player, not something else like the Console.
        if (p == null) {
            Output.get().sendError(sender, SpigotPrison.format(getMessages().getString("Message.CantRunGUIFromConsole")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.openSellAllGUI(p)){
            // If the sender's an admin (OP or have the prison.admin permission) it'll send an error message.
            if (p.hasPermission("prison.admin")) {
                Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllGUIDisabled")));
            }
        }
    }

    @Command(identifier = "sellall add", description = "SellAll add an item to the sellAll shop.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllAddCommand(CommandSender sender,
                                   @Arg(name = "Item_ID", description = "The Item_ID or block to add to the sellAll Shop.") String itemID,
                                   @Arg(name = "Value", description = "The value of the item.") Double value){

        if (!isEnabled()) return;

        if (itemID == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllPleaseAddItem")));
            return;
        }
        itemID = itemID.toUpperCase();

        if (value == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllAddPrice")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.sellAllConfig.getConfigurationSection("Items." + itemID) != null){
            Output.get().sendWarn(sender, SpigotPrison.format(itemID + messages.getString("Message.SellAllAlreadyAdded")));
            return;
        }

        try {
            XMaterial blockAdd;
            try {
                blockAdd = XMaterial.valueOf(itemID);
            } catch (IllegalArgumentException ex){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
                return;
            }

            if (sellAllUtil.addSellAllBlock(blockAdd, value)){
                Output.get().sendInfo(sender, SpigotPrison.format("&3 ITEM [" + itemID + ", " + value + messages.getString("Message.SellAllAddSuccess")));
            }

        } catch (IllegalArgumentException ex){
            Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
        }
    }

    /**
     * <p>This will add the XMaterial and value to the sellall.
     * This will update even if the sellall has not been enabled.
     * </p>
     *
     * @param blockAdd
     * @param value
     */
    public void sellAllAddCommand(XMaterial blockAdd, Double value){

        String itemID = blockAdd.name();

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        // If the block or item was already configured, then skip this:
        if (sellAllUtil.sellAllConfig.getConfigurationSection("Items." + itemID) != null){
            return;
        }

        if (sellAllUtil.addSellAllBlock(blockAdd, value)) return;

        Output.get().logInfo(SpigotPrison.format("&3 ITEM [" + itemID + ", " + value + messages.getString("Message.SellAllAddSuccess")));
    }

    @Command(identifier = "sellall delete", description = "SellAll delete command, remove an item from shop.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllDeleteCommand(CommandSender sender, @Arg(name = "Item_ID", description = "The Item_ID you want to remove.") String itemID){

        if (!isEnabled()) return;

        if (itemID == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllMissingID")));
            return;
        }
        itemID = itemID.toUpperCase();

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.sellAllConfig.getConfigurationSection("Items." + itemID) == null){
            Output.get().sendWarn(sender, SpigotPrison.format(itemID + messages.getString("Message.SellAllNotFoundStringConfig")));
            return;
        }

        if (XMaterial.matchXMaterial(itemID).isPresent()) {
            if (sellAllUtil.removeSellAllBlock(XMaterial.matchXMaterial(itemID).get())) {
                Output.get().sendInfo(sender, SpigotPrison.format(itemID + messages.getString("Message.SellAllDeletedSuccess")));
            }
        }
    }

    @Command(identifier = "sellall edit", description = "SellAll edit command, edit an item of Shop.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllEditCommand(CommandSender sender,
                                    @Arg(name = "Item_ID", description = "The Item_ID or block to add to the sellAll Shop.") String itemID,
                                    @Arg(name = "Value", description = "The value of the item.") Double value){

        if (!isEnabled()) return;

        if (itemID == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllPleaseAddItem")));
            return;
        }
        itemID = itemID.toUpperCase();

        if (value == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllAddPrice")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (sellAllUtil.sellAllConfig.getConfigurationSection("Items." + itemID) == null){
            Output.get().sendWarn(sender, SpigotPrison.format(itemID + messages.getString("Message.SellAllNotFoundEdit")));
            return;
        }

        try {
            XMaterial blockAdd;
            try{
                blockAdd = XMaterial.valueOf(itemID);
            } catch (IllegalArgumentException ex){
                Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
                return;
            }

            if (sellAllUtil.editPrice(blockAdd, value)){
                Output.get().sendInfo(sender, SpigotPrison.format("&3ITEM [" + itemID + ", " + value + messages.getString("Message.SellAllCommandEditSuccess")));
            }

        } catch (IllegalArgumentException ex){
            Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
        }
    }

    @Command(identifier = "sellall multiplier", description = "SellAll multiplier command list", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllMultiplierCommand(CommandSender sender){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.isSellAllMultiplierEnabled){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllMultipliersAreDisabled")));
            return;
        }

        String registeredCmd = Prison.get().getCommandHandler().findRegisteredCommand( "sellall multiplier help" );
        sender.dispatchCommand(registeredCmd);
    }

    @Command(identifier = "sellall multiplier add", description = "SellAll add a multiplier. Permission multipliers for player's prison.sellall.multiplier.<valueHere>, example prison.sellall.multiplier.2 will add a 2x multiplier," +
            "There's also another kind of Multiplier called permission multipliers, they're permissions that you can give to players to give them a multiplier, remember that their format is prison.sellall.multiplier.2 (for example), and this example will give you a " +
            "total of 3x multiplier (1x default + 2x permission = 3x).",
            permissions = "prison.admin", onlyPlayers = false)
    private void sellAllAddMultiplierCommand(CommandSender sender,
                                             @Arg(name = "Prestige", description = "Prestige to hook to the multiplier.") String prestige,
                                             @Arg(name = "multiplier", description = "Multiplier value.") Double multiplier){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.isSellAllMultiplierEnabled){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllMultipliersAreDisabled")));
            return;
        }

        if (sellAllUtil.addPrestigeMultiplier(prestige, multiplier)){
            Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllMultiplierEditSaveSuccess")));
        }
    }

    @Command(identifier = "sellall multiplier delete", description = "SellAll delete a multiplier.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllDeleteMultiplierCommand(CommandSender sender,
                                                @Arg(name = "Prestige", description = "Prestige hooked to the multiplier.") String prestige){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.isSellAllMultiplierEnabled){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllMultipliersAreDisabled")));
            return;
        }

        if (prestige == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllMultiplierFormat")));
            return;
        }

        if (sellAllUtil.sellAllConfig.getConfigurationSection("Multiplier." + prestige) == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllCantFindMultiplier") + prestige + messages.getString("Message.SellAllCantFindMultiplier2")));
            return;
        }

        if (sellAllUtil.removePrestigeMultiplier(prestige)){
            Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllMultiplierDeleteSuccess")));
        }
    }

    @Command(identifier = "sellall Trigger", description = "Toggle SellAll Shift+Right Click on a tool to trigger the /sellall sell command, true -> Enabled or False -> Disabled.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllToolsTriggerToggle(CommandSender sender,
                                           @Arg(name = "Boolean", description = "Enable or disable", def = "null") String enable){

        if (!isEnabled()) return;

        if (enable.equalsIgnoreCase("null")){
            String registeredCmd = Prison.get().getCommandHandler().findRegisteredCommand( "sellall toolsTrigger help" );
            sender.dispatchCommand(registeredCmd);
            return;
        }

        if (!enable.equalsIgnoreCase("true") && !enable.equalsIgnoreCase("false")){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.InvalidBooleanInput")));
            return;
        }

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        boolean enableInput = getBoolean(enable);
        if (sellAllUtil.isSellAllItemTriggerEnabled == enableInput) {
            if (sellAllUtil.isSellAllItemTriggerEnabled) {
                Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerAlreadyEnabled")));
            } else {
                Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerAlreadyDisabled")));
            }
            return;
        }

        if (sellAllUtil.setItemTrigger(enableInput)){
            if (enableInput){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerEnabled")));
            } else {
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerDisabled")));
            }
        }
    }

    @Command(identifier = "sellall Trigger add", description = "Add an Item to trigger the Shift+Right Click -> /sellall sell command.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllTriggerAdd(CommandSender sender,
                                   @Arg(name = "Item", description = "Item name") String itemID){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.isSellAllItemTriggerEnabled){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerIsDisabled")));
            return;
        }

        if (itemID == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerMissingItem")));
            return;
        }
        itemID = itemID.toUpperCase();

        try {
            XMaterial blockAdd;
            try{
                blockAdd = XMaterial.valueOf(itemID);
            } catch (IllegalArgumentException ex){
                Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
                return;
            }

            if (sellAllUtil.addItemTrigger(blockAdd)){
                Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerItemAddSuccess") + " [" + itemID + "]"));
            }
        } catch (IllegalArgumentException ex){
            Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
        }
    }

    @Command(identifier = "sellall Trigger delete", description = "Delete an Item from the Shift+Right Click trigger -> /sellall sell command.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllTriggerDelete(CommandSender sender,
                                      @Arg(name = "Item", description = "Item name") String itemID){

        if (!isEnabled()) return;

        SellAllUtil sellAllUtil = SellAllUtil.get();
        if (sellAllUtil == null){
            return;
        }

        if (!sellAllUtil.isSellAllItemTriggerEnabled){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerIsDisabled")));
            return;
        }

        if (itemID == null){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerMissingItem")));
            return;
        }
        itemID = itemID.toUpperCase();

        if (!XMaterial.matchXMaterial(itemID).isPresent()){
            Output.get().sendError(sender, SpigotPrison.format(messages.getString("Message.SellAllWrongID") + " [" + itemID + "]"));
            return;
        }
        XMaterial xMaterial = XMaterial.matchXMaterial(itemID).get();

        if (!sellAllUtil.getItemTriggerXMaterials().contains(xMaterial)){
            Output.get().sendWarn(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerMissingItem")));
            return;
        }

        if (sellAllUtil.removeItemTrigger(xMaterial)) {
            Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllTriggerItemDeleteSuccess") + " [" + itemID + "]"));
        }
    }

    @Command(identifier = "sellall setdefault", description = "SellAll default values ready to go.", permissions = "prison.admin", onlyPlayers = false)
    private void sellAllSetDefaultCommand(CommandSender sender){

        if (!isEnabled()) return;

        // Setup all the prices in sellall:
        SpigotPlatform platform = (SpigotPlatform) Prison.get().getPlatform();
        for ( SellAllBlockData xMatCost : platform.buildBlockListXMaterial() ) {

            // Add blocks to sellall:
            sellAllAddCommand(sender, xMatCost.getBlock().name(), xMatCost.getPrice() );
        }

        Output.get().sendInfo(sender, SpigotPrison.format(messages.getString("Message.SellAllDefaultSuccess")));
    }
}

