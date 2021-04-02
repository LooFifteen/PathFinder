package pathfinder;

import de.bossascrew.core.player.PlayerHandler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import pathfinder.data.DatabaseModel;
import pathfinder.data.FoundInfo;
import pathfinder.handler.RoadMapHandler;
import pathfinder.util.AStar;
import pathfinder.util.AStarNode;
import pathfinder.util.Path;

import java.util.*;

public class PathPlayer {

    @Getter
    private int globalPlayerId;
    @Getter
    private UUID uuid;

    private Map<Integer, FoundInfo> foundInfos;

    private Map<Integer, Path> activePaths;

    private int editModeRoadMapId;
    @Getter
    private int selectedRoadMapId;

    public PathPlayer(int globalPlayerId) {
        this.globalPlayerId = globalPlayerId;
        this.uuid = PlayerHandler.getInstance().getGlobalPlayer(globalPlayerId).getPlayerId();

        foundInfos = DatabaseModel.getInstance().loadFoundNodes(globalPlayerId);
    }

    public void findNode(Node node, boolean group) {
        if(group) {
            findGroup(node);
        } else {
            findNode(node);
        }
    }

    public void findGroup(Node node) {
        RoadMap roadMap = RoadMapHandler.getInstance().getRoadMap(node.getRoadMapId());
        NodeGroup group = roadMap.getNodeGroup(node.getNodeGroupId());

        if(group == null) {
            findNode(node);
        } else {
            for(Node groupedNode : group.getNodes()) {
                findNode(groupedNode);
            }
        }
    }

    public void findNode(Node node) {
        assert !hasFound(node.getDatabaseId());

        FoundInfo info = DatabaseModel.getInstance().newFoundInfo(globalPlayerId, node.getDatabaseId(), new Date());
        assert info != null;
        foundInfos.put(info.getDatabaseId(), info);
    }

    public void unfindNode(Node node, boolean group) {
        if(group) {
            unfindGroup(node);
        } else {
            unfindNode(node);
        }
    }

    public void unfindGroup(Node node) {
        RoadMap roadMap = RoadMapHandler.getInstance().getRoadMap(node.getRoadMapId());
        NodeGroup group = roadMap.getNodeGroup(node);
        assert group != null;
        for(Node n : group.getNodes()) {
            unfindNode(n.getDatabaseId());
        }
    }

    public void unfindNode(Node node) {
        unfindNode(node.getDatabaseId());
    }

    public void unfindNode(int nodeId) {
        FoundInfo toRemove = null;
        for(FoundInfo info : foundInfos.values()) {
            if(info.getNodeId() == nodeId)
                toRemove = info;
        }
        assert toRemove != null;
        foundInfos.remove(toRemove.getDatabaseId());

        DatabaseModel.getInstance().deleteFoundNode(toRemove);
    }

    public boolean hasFound(int nodeId) {
        return hasFound(foundInfos.values(), nodeId);
    }

    public boolean hasFound(Collection<FoundInfo> infos, int nodeId) {
        for(FoundInfo info : infos) {
            if(info.getNodeId() == nodeId) return true;
        }
        return false;
    }

    public void setPath(Node targetNode) {
        Player player = Bukkit.getPlayer(uuid);
        assert player != null;
        setPath(player, targetNode);
    }

    public void setPath(Player player, Node targetNode) {

        AStar aStar = new AStar();
        //aStar.aStarSearch(startNode, goalNode);
        //return aStar.printPath(goalNode);

        //TODO astarmap aus roadmap erzeugen, astar aufruf, path erzeugen und setzen
    }

    public void setPath(Path path) {
        assert path != null;
        activePaths.put(path.getRoadMapId(), path);
    }

    public void cancelPaths() {
        for(Path path : activePaths.values()) {
            path.cancel();
        }
        activePaths.clear();
    }

    public void cancelPath(RoadMap roadMap) {
        Path toBeCancelled = activePaths.get(roadMap.getDatabaseId());
        assert toBeCancelled != null;

        toBeCancelled.cancel();
        activePaths.remove(roadMap.getDatabaseId());
    }

    public void pauseActivePath(RoadMap roadMap) {
        Path active = activePaths.get(roadMap.getDatabaseId());
        assert active != null;
        active.cancel();
    }

    public void pauseActivePaths() {
        for(Path path : activePaths.values()) {
            path.cancel();
        }
    }

    public void resumePausedPath(RoadMap roadMap) {
        Path paused = activePaths.get(roadMap.getDatabaseId());
        assert paused != null;
        paused.run();
    }

    public void resumePausedPaths() {
        for(Path path : activePaths.values()) {
            path.run();
        }
    }

    public void setEditMode(int roadMapId) {
        this.editModeRoadMapId = roadMapId;
        this.selectedRoadMapId = roadMapId;
    }

    public void clearEditMode() {
        RoadMap roadMap = RoadMapHandler.getInstance().getRoadMap(editModeRoadMapId);
        if(roadMap != null) {
            roadMap.setEditMode(uuid, false);
        }
        setEditMode(-1);
    }

    public boolean isEditing() {
        return editModeRoadMapId != -1;
    }

    public RoadMap getEdited() {
        return RoadMapHandler.getInstance().getRoadMap(editModeRoadMapId);
    }

    public void setSelectedRoadMap(int roadMapId) {
        this.selectedRoadMapId = roadMapId;
    }

    public void deselectRoadMap() {
        deselectRoadMap(selectedRoadMapId);
    }

    public void deselectRoadMap(int id) {
        if(selectedRoadMapId == id)
            selectedRoadMapId = -1;
    }

    public AStarNode asNode(Location location) {
        return new AStarNode(-1, 0);
    }
}
