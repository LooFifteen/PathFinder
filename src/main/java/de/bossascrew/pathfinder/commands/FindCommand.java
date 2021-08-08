package de.bossascrew.pathfinder.commands;

import de.bossascrew.acf.BaseCommand;
import de.bossascrew.acf.annotation.*;
import de.bossascrew.core.base.ComponentMenu;
import de.bossascrew.core.base.Menu;
import de.bossascrew.core.bukkit.player.PlayerUtils;
import de.bossascrew.core.util.ComponentUtils;
import de.bossascrew.pathfinder.PathPlugin;
import de.bossascrew.pathfinder.data.FindableGroup;
import de.bossascrew.pathfinder.data.PathPlayer;
import de.bossascrew.pathfinder.data.RoadMap;
import de.bossascrew.pathfinder.data.findable.Findable;
import de.bossascrew.pathfinder.handler.PathPlayerHandler;
import de.bossascrew.pathfinder.util.AStarUtils;
import de.bossascrew.pathfinder.util.CommandUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.awt.*;

@CommandAlias("finde|find")
public class FindCommand extends BaseCommand {

    @CatchUnknown
    @HelpCommand
    public void onDefault(Player player) {
        Menu menu = new Menu("Finde Orte einer Stadtkarte mit folgenden Befehlen:");
        if(player.hasPermission(PathPlugin.PERM_COMMAND_FIND_LOCATIONS)) {
            menu.addSub(new ComponentMenu(ComponentUtils.getCommandComponent("/find ort <Ort>")));
        }
        if(player.hasPermission(PathPlugin.PERM_COMMAND_FIND_ITEMS)) {
            if(PathPlugin.getInstance().isTraders() || PathPlugin.getInstance().isQuests() || (PathPlugin.getInstance().isBentobox()) && PathPlugin.getInstance().isChestShop()) {
                menu.addSub(new ComponentMenu(ComponentUtils.getCommandComponent("/find item <Item>")));
            }
        }
        if(player.hasPermission(PathPlugin.PERM_COMMAND_FIND_QUESTS)) {
            if (PathPlugin.getInstance().isQuests()) {
                menu.addSub(new ComponentMenu(ComponentUtils.getCommandComponent("/find quest <Quest>")));
            }
        }
        if(player.hasPermission(PathPlugin.PERM_COMMAND_FIND_TRADERS)) {
            if (PathPlugin.getInstance().isTraders()) {
                menu.addSub(new ComponentMenu(ComponentUtils.getCommandComponent("/find shop <Shop>")));
            }
        }
        if(menu.hasSubs()) {
            PlayerUtils.sendComponents(player, menu.toComponents());
        } else {
            PlayerUtils.sendMessage(player, ChatColor.RED + "Dir fehlen nötige Berechtigungen für diesen Befehl.");
        }
    }

    @Subcommand("ort")
    @Syntax("<Ort>")
    @CommandPermission(PathPlugin.PERM_COMMAND_FIND_LOCATIONS)
    @CommandCompletion(PathPlugin.COMPLETE_FINDABLE_LOCATIONS)
    public void onFindeOrt(Player player, String searched) {
        RoadMap roadMap = CommandUtils.getAnyRoadMap(player.getWorld());
        if(roadMap == null) {
            PlayerUtils.sendMessage(player, ChatColor.RED + "Keine Straßenkarte gefunden.");
            return;
        }
        if(!roadMap.getWorld().equals(player.getWorld())) {
            PlayerUtils.sendMessage(player, ChatColor.RED + "Diese Straßenkarte liegt nicht in deiner aktuellen Welt.");
            return;
        }
        PathPlayer pp = PathPlayerHandler.getInstance().getPlayer(player);
        if(pp == null) {
            return;
        }

        Findable f = roadMap.getFindables().stream()
                .filter(findable -> findable.getGroup() == null)
                .filter(findable -> findable.getName().equalsIgnoreCase(searched))
                .findFirst().orElse(null);
        if(f == null) {
            FindableGroup group = roadMap.getGroups().values().stream()
                    .filter(FindableGroup::isFindable)
                    .filter(g -> g.getName().equalsIgnoreCase(searched))
                    .filter(g -> pp.hasFound(g.getDatabaseId(), true))
                    .findAny().orElse(null);
            if(group == null) {
                PlayerUtils.sendMessage(player, ChatColor.RED + "Es gibt kein Ziel mit diesem Namen.");
                return;
            }
            f = group.getFindables().stream().findAny().orElse(null);
        }
        if (f == null) {
            PlayerUtils.sendMessage(player, ChatColor.RED + "Ein Fehler ist aufgetreten.");
            return;
        }
        AStarUtils.startPath(player, f, true);
    }
}
