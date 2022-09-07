package de.cubbossa.pathfinder.core.roadmap;

import com.google.common.collect.Lists;
import de.cubbossa.pathfinder.Named;
import de.cubbossa.pathfinder.PathPlugin;
import de.cubbossa.pathfinder.core.events.node.EdgesCreatedEvent;
import de.cubbossa.pathfinder.core.events.node.EdgesDeletedEvent;
import de.cubbossa.pathfinder.core.events.node.NodeCreatedEvent;
import de.cubbossa.pathfinder.core.events.node.NodesDeletedEvent;
import de.cubbossa.pathfinder.core.node.*;
import de.cubbossa.pathfinder.core.node.implementation.PlayerNode;
import de.cubbossa.pathfinder.core.node.implementation.Waypoint;
import de.cubbossa.pathfinder.data.DataStorageException;
import de.cubbossa.pathfinder.module.visualizing.visualizer.PathVisualizer;
import de.cubbossa.pathfinder.util.NodeSelection;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jgrapht.Graph;
import org.jgrapht.alg.util.Triple;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Getter
@Setter
public class RoadMap implements Keyed, Named {

	private final NamespacedKey key;
	private String nameFormat;
	private Component displayName;
	private double defaultBezierTangentLength;

	private final Map<Integer, Node> nodes;
	private final Collection<Edge> edges;

	private PathVisualizer<?, ?> visualizer;

	public RoadMap(NamespacedKey key, String name, PathVisualizer<?, ?> visualizer, double defaultBezierTangentLength) {

		this.key = key;
		this.setNameFormat(name);
		this.defaultBezierTangentLength = defaultBezierTangentLength;

		this.nodes = new TreeMap<>();
		this.edges = new HashSet<>();

		setVisualizer(visualizer);
	}

	public void setNameFormat(String nameFormat) {
		this.nameFormat = nameFormat;
		this.displayName = PathPlugin.getInstance().getMiniMessage().deserialize(nameFormat);
	}

	public void loadNodesAndEdges() {
		nodes.clear();
		for (var entry : PathPlugin.getInstance().getDatabase().loadNodes(this).entrySet()) {
			nodes.put(entry.getKey(), entry.getValue());
			System.out.println(entry.getKey());
		}
		for (var entry : PathPlugin.getInstance().getDatabase().loadNodeGroupNodes().entrySet()) {
			NodeGroup group = NodeGroupHandler.getInstance().getNodeGroup(entry.getKey());
			if (group == null) {
				continue;
			}
			for (int i : entry.getValue()) {
				Node node = nodes.get(i);
				if (node == null) {
					PathPlugin.getInstance().getLogger().log(Level.SEVERE, "Node is null: " + i);
					continue;
				}
				if(node instanceof Groupable groupable) {
					group.add(groupable);
				}
			}
		}

		edges.clear();
		edges.addAll(PathPlugin.getInstance().getDatabase().loadEdges(this));
		for (Edge edge : edges) {
			edge.getStart().getEdges().add(edge);
		}
	}

	public Graph<Node, Edge> toGraph(@Nullable PlayerNode player) {
		Graph<Node, Edge> graph = new SimpleDirectedWeightedGraph<>(Edge.class);
		nodes.values().forEach(graph::addVertex);
		edges.forEach(e -> graph.addEdge(e.getStart(), e.getEnd(), e));
		edges.forEach(e -> graph.setEdgeWeight(e, e.getWeightedLength()));

		if(player != null) {
			Location playerLocation = player.getLocation();
			graph.addVertex(player);
			List<Triple<Node, Double, Integer>> triples = nodes.values().stream()
					.filter(node -> Objects.equals(node.getLocation().getWorld(), playerLocation.getWorld()))
					.map(node -> new AbstractMap.SimpleEntry<>(node, node.getLocation().distance(playerLocation)))
					.sequential()
					.sorted(Comparator.comparingDouble(AbstractMap.SimpleEntry::getValue))
					.limit(10)
					.map(e -> {
						Node n = e.getKey();
						Vector dir = n.getLocation().toVector().clone().add(new Vector(0, .5f, 0)).subtract(playerLocation.toVector());
						double length = dir.length();
						dir.normalize();
						Location loc = player.getLocation().setDirection(dir);
						int count = 1;

						BlockIterator iterator = new BlockIterator(loc, 0, (int) length);
						int cancel = 0; // sometimes the while loop does not cancel without extra counter
						while (iterator.hasNext() && cancel++ < 100) {
							Block block = iterator.next();
							if (block.getType().isBlock() && block.getType().isSolid()) {
								count++;
							}
						}
						return new Triple<>(n, length, count);
					})
					.collect(Collectors.toList());

			boolean anyNullCount = triples.stream().anyMatch(e -> e.getThird() == 1);

			triples.stream()
					.filter(e -> !anyNullCount || e.getThird() == 1)
					.forEach(e -> {

						Edge edge = new Edge(player, e.getFirst(), e.getThird() * 10_000); // prefer paths without interfering blocks
						graph.addEdge(player, e.getFirst(), edge);
						graph.setEdgeWeight(edge, e.getSecond() * edge.getWeightModifier());
					});
		}

		return graph;
	}

	public NavigateSelection getNavigables() {
		return new NavigateSelection(NodeGroupHandler.getInstance().getNodeGroups().stream()
				.filter(group -> nodes.values().stream().anyMatch(group::contains))
				.collect(Collectors.toSet()));
	}

	public NavigateSelection getNavigables(String... keywords) {
		return getNavigables(Lists.newArrayList(keywords));
	}

	public NavigateSelection getNavigables(Collection<String> keywords) {
		return getNavigables().stream()
				.filter(node -> {
					for (String keyword : keywords) {
						if (node.getSearchTerms().contains(keyword)) {
							return true;
						}
					}
					return false;
				})
				.collect(Collectors.toCollection(NavigateSelection::new));
	}

	public Waypoint createWaypoint(Location location) {
		return createNode(RoadMapHandler.WAYPOINT_TYPE, location);
	}

	public <T extends Node> T createNode(NodeType<T> type, Location location, NodeGroup... groups) {

		T node = type.getFactory().apply(new NodeType.NodeCreationContext(
				this,
				RoadMapHandler.getInstance().requestNodeId(),
				location
		));

		addNode(node);
		if (node instanceof Groupable groupable) {
			Collection<Groupable> col = Collections.singleton(groupable);
			for (NodeGroup group : groups) {
				NodeGroupHandler.getInstance().addNodes(group, col);
			}
		}
		Bukkit.getScheduler().runTask(PathPlugin.getInstance(), () -> {
			Bukkit.getPluginManager().callEvent(new NodeCreatedEvent(node));
		});
		return node;
	}

	public void addNode(Node node) {
		nodes.put(node.getNodeId(), node);
	}

	public void removeNodes(NodeSelection selection) {
		removeNodes(selection.toArray(Node[]::new));
	}

	public void removeNodes(int id) {
		Node node = getNode(id);
		if (node != null) {
			removeNodes(node);
		}
	}

	public void removeNodes(Node... nodes) {
		Collection<Edge> deleteEdges = new HashSet<>();
		Collection<Node> deleteNodes = Lists.newArrayList(nodes);

		for (Node node : nodes) {
			for (Edge edge : getEdgesAt(node)) {
				edge.getEnd().getEdges().remove(edge);
				edges.remove(edge);

				deleteEdges.add(edge);
			}
			this.nodes.remove(node.getNodeId());
		}
		Bukkit.getScheduler().runTask(PathPlugin.getInstance(), () -> {
			Bukkit.getPluginManager().callEvent(new EdgesDeletedEvent(deleteEdges));
			Bukkit.getPluginManager().callEvent(new NodesDeletedEvent(deleteNodes));
		});
	}

	public @Nullable
	Node getNode(int nodeId) {
		for (Node node : nodes.values()) {
			if (node.getNodeId() == nodeId) {
				return node;
			}
		}
		return null;
	}

	public Collection<Node> getNodesByGroup(NodeGroup group) {
		return nodes.values().stream()
				.filter(node -> node instanceof Groupable groupable && groupable.getGroups().contains(group))
				.collect(Collectors.toSet());
	}

	public Edge getEdge(Node start, Node end) {
		return edges.stream().filter(edge -> edge.getStart().equals(start) && edge.getEnd().equals(end)).findFirst().orElse(null);
	}

	public Edge connectNodes(Node start, Node end) {
		return connectNodes(start, end, false, 1, 1);
	}

	/**
	 * Connects two nodes with an edge. Edges are stored directed, therefore it must be stated if the new node should
	 * only be from start to end or also from end to start.
	 * <p>
	 * This method calls the corresponding {@link EdgesCreatedEvent}. This will always be called sync, so that the
	 * method can be called async.
	 *
	 * @param start    The node to start the edge from.
	 * @param end      The node to end the edge at.
	 * @param directed If another edge should be created from end to start. Prefer this method against calling it twice,
	 *                 as the edit mode particle setup has to be recalculated for each edge change.
	 * @return the created edge from start to end.
	 */
	public Edge connectNodes(Node start, Node end, boolean directed) {
		if (start.equals(end)) {
			throw new IllegalArgumentException("Cannot connect node with itself.");
		}
		Edge edge;
		try {
			edge = new Edge(start, end, 1);
		} catch (DataStorageException e) {
			throw new IllegalArgumentException("Error while connecting edges: " + start + " and " + end, e);
		}

		start.getEdges().add(edge);
		edges.add(edge);

		Edge other = edge;
		if (!directed) {
			Edge existing = getEdge(end, start);
			if (existing == null) {
				try {
					other = new Edge(start, end, 1);
				} catch (DataStorageException e) {
					throw new IllegalArgumentException("Error while connecting edges: " + start + " and " + end, e);
				}
				end.getEdges().add(other);
				edges.add(other);
			}
		}

		Edge finalOther = other;
		Bukkit.getScheduler().runTask(PathPlugin.getInstance(), () -> Bukkit.getPluginManager().callEvent(new EdgesCreatedEvent(edge, finalOther)));

		return edge;
	}

	/**
	 * Connects two nodes with an edge. Edges are stored directed, therefore it must be stated if the new node should
	 * only be from start to end or also from end to start.
	 * <p>
	 * This method calls the corresponding {@link EdgesCreatedEvent}. This will always be called sync, so that the
	 * method can be called async.
	 *
	 * @param start      The node to start the edge from.
	 * @param end        The node to end the edge at.
	 * @param directed   If another edge should be created from end to start. Prefer this method against calling it twice,
	 *                   as the edit mode particle setup has to be recalculated for each edge change.
	 * @param weight     The weight modifier for this edge. This will be taken into account when calculating the shortest path.
	 *                   The actual length of the edge will be multiplied with the modifier, so when players have to crouch along some
	 *                   edges of your roadmap, you might want to change the modifier to 2.
	 * @param weightBack Same as weight, but for the directed edge in the opposite direction.
	 * @return the created edge from start to end.
	 */
	public Edge connectNodes(Node start, Node end, boolean directed, float weight, float weightBack) {
		if (start.equals(end)) {
			throw new IllegalArgumentException("Cannot connect node with itself.");
		}
		Edge edge = new Edge(start, end, weight);

		start.getEdges().add(edge);
		edges.add(edge);

		Edge other = edge;
		if (!directed) {
			Edge existing = getEdge(end, start);
			if (existing == null) {
				other = new Edge(end, start, weightBack);
				end.getEdges().add(other);
				edges.add(other);
			}
		}

		Edge finalOther = other;
		Bukkit.getScheduler().runTask(PathPlugin.getInstance(), () -> Bukkit.getPluginManager().callEvent(new EdgesCreatedEvent(edge, finalOther)));

		return edge;
	}

	public void disconnectNodes(Node start, Node end) {
		disconnectNodes(getEdge(start, end));
	}

	public void disconnectNode(Node node) {
		node.getEdges().forEach(this::disconnectNodes);
	}

	public void disconnectNodes(Edge edge) {
		if (edge == null) {
			return;
		}
		edge.getStart().getEdges().remove(edge);
		edge.getEnd().getEdges().remove(edge);
		edges.remove(edge);

		Bukkit.getScheduler().runTask(PathPlugin.getInstance(), () -> Bukkit.getPluginManager().callEvent(new EdgesDeletedEvent(edge)));
	}

	public void delete() {
		PathPlugin.getInstance().getDatabase().deleteRoadMap(this);
	}

	public Collection<Edge> getEdgesFrom(Node node) {
		return node.getEdges();
	}

	public Collection<Edge> getEdgesTo(Node node) {
		Collection<Edge> ret = new ArrayList<>();
		for (Edge edge : edges) {
			if (edge.getEnd().equals(node)) {
				ret.add(edge);
			}
		}
		return ret;
	}

	public Collection<Edge> getEdgesAt(Node node) {
		return edges.stream().filter(edge -> edge.getStart().equals(node) || edge.getEnd().equals(node)).collect(Collectors.toSet());
	}

	public Collection<Node> getNodes() {
		return nodes.values();
	}

	public @Nullable
	Edge getEdge(int aId, int bId) {
		return edges.stream()
				.filter(edge -> edge.getStart().getNodeId() == aId && edge.getEnd().getNodeId() == bId)
				.findAny()
				.orElse(null);
	}

	public RoadMapBatchEditor getBatchEditor() {
		AtomicBoolean closed = new AtomicBoolean(false);

		return new RoadMapBatchEditor() {
			@Override
			public <T extends Node> void createNode(NodeType<T> type, Vector vector, String permission, NodeGroup... groups) {
				if (closed.get()) {
					throw new IllegalStateException("Batch Editor already closed.");
				}
			}

			@Override
			public void commit() {
				if (closed.get()) {
					throw new IllegalStateException("Batch Editor already closed.");
				}


				closed.set(true);
			}
		};
	}

	public interface RoadMapBatchEditor {

		<T extends Node> void createNode(NodeType<T> type, Vector vector, String permission, NodeGroup... groups);

		void commit();
	}
}
