package de.bossascrew.pathfinder.node;

import com.google.common.collect.Sets;
import de.bossascrew.pathfinder.PathPlugin;
import de.bossascrew.pathfinder.roadmap.RoadMap;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Getter
@Setter
public class Waypoint implements Node, Findable, Navigable {

	protected final int nodeId;
	protected final NamespacedKey roadMapKey;
	protected final RoadMap roadMap;
	protected final List<Edge> edges;

	protected Vector position;
	protected String nameFormat;
	protected Component displayName;
	@Nullable
	protected NamespacedKey groupKey = null;
	@Nullable
	protected String permission = null;
	@Nullable
	protected Double bezierTangentLength = null;
	//TODO necessary? More than 1 group per node instead?
	protected Collection<String> searchTerms;

	public Waypoint(int databaseId, RoadMap roadMap, @Nullable String nameFormat) {
		this.nodeId = databaseId;
		this.roadMap = roadMap;
		this.roadMapKey = roadMap.getKey();
		this.setNameFormat(nameFormat);

		edges = new ArrayList<>();
	}

	public void setNameFormat(String nameFormat) {
		this.nameFormat = nameFormat;
		this.displayName = PathPlugin.getInstance().getMiniMessage().deserialize(nameFormat);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Waypoint waypoint)) {
			return false;
		}

		return nodeId == waypoint.nodeId;
	}

	@Override
	public int hashCode() {
		return nodeId;
	}

	@Override
	public Location getLocation() {
		return position.toLocation(roadMap.getWorld());
	}

	@Override
	public Edge connect(Node target) {
		return roadMap.connectNodes(this, target);
	}

	@Override
	public void disconnect(Node target) {
		roadMap.disconnectNodes(this, target);
	}

	@Override
	public int compareTo(@NotNull Node o) {
		return Integer.compare(nodeId, o.getNodeId());
	}

	@Override
	public Collection<Node> getGroup() {
		return Sets.newHashSet(this);
	}
}
