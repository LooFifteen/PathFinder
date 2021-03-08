package main.de.bossascrew.pathfinder.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;

import de.tr7zw.nbtapi.NBTItem;
import main.de.bossascrew.pathfinder.RoadMap;
import main.de.bossascrew.pathfinder.data.Message;
import main.de.bossascrew.pathfinder.data.Permission;
import main.de.bossascrew.pathfinder.inventories.InventoryNodeEditor;
import main.de.bossascrew.pathfinder.system.Node;
import main.de.bossascrew.pathfinder.visualization.EdgeCreator;
import main.de.bossascrew.pathfinder.visualization.VisualizerEditMode;

public class ArmorstandInteractListener implements Listener {

	@EventHandler
	public void onUncloth(PlayerArmorStandManipulateEvent event) {
		if(event.getPlayer() == null) return;
		if(event.getRightClicked().getName().contains(VisualizerEditMode.WAYPOINT_NAME)) {
			event.setCancelled(true);
			Player p = event.getPlayer();
			if(p.hasPermission(Permission.EDGE_CREATE)) {
				NBTItem i = new NBTItem(event.getArmorStandItem());
				RoadMap rm = RoadMap.getRoadMap(i.getString(VisualizerEditMode.ROADMAP_KEY));
				if(rm == null) return;
				int id = Integer.parseInt(i.getString(VisualizerEditMode.IDENTIFIER_KEY));
				Node n = rm.getFile().getNode(id);
				if(n == null) return;
				
				EdgeCreator edgeCreator = rm.getEdgeManager().getCreator(p.getUniqueId());
				if(edgeCreator != null) {
					edgeCreator.setEnd(n);
					p.sendMessage(Message.EDGE_CREATION_COMPLETE);
				} else {
					InventoryNodeEditor inv = InventoryNodeEditor.getInv(n.id);
					if(inv == null) inv = new InventoryNodeEditor(rm, n);
					inv.open(p);
				}
			} 
		} else if(event.getRightClicked().getName().equals(VisualizerEditMode.EDGE_NAME)) {
			event.setCancelled(true);
			Player p = event.getPlayer();
			if(p.hasPermission(Permission.EDGE_REMOVE)) {
				NBTItem i = new NBTItem(event.getArmorStandItem());
				RoadMap rm = RoadMap.getRoadMap(i.getString(VisualizerEditMode.ROADMAP_KEY));
				if(rm == null) return;
				
				int id1 = Integer.parseInt(i.getString(VisualizerEditMode.IDENTIFIER_KEY).split("###")[0]);
				int id2 = Integer.parseInt(i.getString(VisualizerEditMode.IDENTIFIER_KEY).split("###")[1]);
				
				rm.removeEdge(id1, id2);
				event.getRightClicked().remove();
			}
		}
	}
}
