package com.rspaintextended;

import com.rspaintextended.RsPaintExtendedPlugin.Stroke;
import com.rspaintextended.RsPaintExtendedPlugin.StrokePoint;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyMemberMessage;
import net.runelite.client.party.messages.PartyMessage;
import net.runelite.client.party.messages.UserSync;

/** Sends the local canvas to party members and keeps a copy of each other member's canvas. */
@Slf4j
@Singleton
public class PartyCanvasSync
{
	private static final long FLUSH_INTERVAL_MS = 200;
	private static final int MAX_POINTS_PER_MESSAGE = 1000;

	@Inject private ClientThread clientThread;
	@Inject private PartyService partyService;
	@Inject private WSClient wsClient;
	@Inject private StrokeClock clock;

	// Client thread only.
	private final Map<Long, MemberCanvas> canvases = new HashMap<>();
	// Changed from the Swing thread by the side panel.
	private final Set<Long> hiddenMembers = ConcurrentHashMap.newKeySet();

	// Outgoing changes waiting for the next flush. Client thread only.
	private final Map<Stroke, List<StrokePoint>> pendingPoints = new LinkedHashMap<>();
	private final List<String> pendingErases = new ArrayList<>();
	private boolean fullSyncPending = false;
	private boolean canvasRequestPending = false;
	private long lastFlush = 0;

	void startUp()
	{
		wsClient.registerMessage(RsPaintStrokeAppend.class);
		wsClient.registerMessage(RsPaintStrokeErase.class);
		wsClient.registerMessage(RsPaintCanvasSync.class);
		wsClient.registerMessage(RsPaintCanvasClear.class);
		wsClient.registerMessage(RsPaintCanvasRequest.class);

		clientThread.invokeLater(() -> {
			fullSyncPending = true;
			canvasRequestPending = true;
		});
	}

	void shutDown()
	{
		wsClient.unregisterMessage(RsPaintStrokeAppend.class);
		wsClient.unregisterMessage(RsPaintStrokeErase.class);
		wsClient.unregisterMessage(RsPaintCanvasSync.class);
		wsClient.unregisterMessage(RsPaintCanvasClear.class);
		wsClient.unregisterMessage(RsPaintCanvasRequest.class);

		// The overlay is already removed, so no flush can follow this clear.
		clientThread.invokeLater(() -> {
			if (canSend())
			{
				send(new RsPaintCanvasClear());
			}
			canvases.clear();
			clearPending();
		});
		hiddenMembers.clear();
	}

	boolean isMemberVisible(long memberId)
	{
		return !hiddenMembers.contains(memberId);
	}

	void setMemberVisible(long memberId, boolean visible)
	{
		if (visible)
		{
			hiddenMembers.remove(memberId);
		}
		else
		{
			hiddenMembers.add(memberId);
		}
	}

	void forEachVisibleStroke(int[] regions, Consumer<Stroke> action)
	{
		if (regions == null)
		{
			return;
		}

		for (Map.Entry<Long, MemberCanvas> entry : canvases.entrySet())
		{
			if (hiddenMembers.contains(entry.getKey()))
			{
				continue;
			}

			for (int region : regions)
			{
				List<Stroke> strokes = entry.getValue().strokesByRegion.get(region);
				if (strokes != null)
				{
					strokes.forEach(action);
				}
			}
		}
	}

	/** Returns a copy of the member's strokes. Client thread only. */
	List<SyncedStroke> memberStrokes(long memberId)
	{
		List<SyncedStroke> result = new ArrayList<>();
		MemberCanvas canvas = canvases.get(memberId);
		if (canvas != null)
		{
			for (Stroke stroke : canvas.strokesById.values())
			{
				result.add(toSynced(stroke, stroke.pts));
			}
		}
		return result;
	}

	/** Returns a copy of the strokes of all members that are not hidden. Client thread only. */
	List<SyncedStroke> visibleMemberStrokes()
	{
		List<SyncedStroke> result = new ArrayList<>();
		for (Long memberId : canvases.keySet())
		{
			if (!hiddenMembers.contains(memberId))
			{
				result.addAll(memberStrokes(memberId));
			}
		}
		return result;
	}

	/** Sends the complete local canvas on the next flush. Client thread only. */
	void requestFullSync()
	{
		fullSyncPending = true;
	}

	void onPointAdded(Stroke stroke, StrokePoint point)
	{
		if (partyService.isInParty())
		{
			pendingPoints.computeIfAbsent(stroke, k -> new ArrayList<>()).add(point);
		}
	}

	void onStrokesErased(Collection<Stroke> strokes)
	{
		if (!partyService.isInParty())
		{
			return;
		}

		for (Stroke stroke : strokes)
		{
			pendingPoints.remove(stroke);
			pendingErases.add(stroke.id);
		}
	}

	void onCanvasCleared()
	{
		clearPending();
		if (canSend())
		{
			send(new RsPaintCanvasClear());
		}
	}

	/** Called on each client tick. Sends at most once per FLUSH_INTERVAL_MS. */
	void flush(Map<Integer, List<Stroke>> localStrokesByRegion)
	{
		long now = System.currentTimeMillis();
		if (now - lastFlush < FLUSH_INTERVAL_MS)
		{
			return;
		}
		lastFlush = now;

		if (!canSend())
		{
			clearPending();
			return;
		}

		// Drop the pending changes even if a send fails, so the error does not repeat on every tick.
		try
		{
			sendPending(localStrokesByRegion);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not send canvas changes to the party", e);
		}
		finally
		{
			pendingPoints.clear();
			pendingErases.clear();
		}
	}

	private void sendPending(Map<Integer, List<Stroke>> localStrokesByRegion)
	{
		if (canvasRequestPending)
		{
			canvasRequestPending = false;
			send(new RsPaintCanvasRequest());
		}

		if (fullSyncPending)
		{
			fullSyncPending = false;
			List<Stroke> strokes = new ArrayList<>();
			localStrokesByRegion.values().forEach(strokes::addAll);
			// Receivers sort by the order in each stroke. Old versions of the plugin use the message order.
			strokes.sort(Stroke.DRAW_ORDER);
			sendFullSync(strokes);
		}

		if (!pendingPoints.isEmpty())
		{
			List<SyncedStroke> strokes = new ArrayList<>();
			for (Map.Entry<Stroke, List<StrokePoint>> entry : pendingPoints.entrySet())
			{
				strokes.add(toSynced(entry.getKey(), entry.getValue()));
			}
			send(new RsPaintStrokeAppend(strokes));
		}

		if (!pendingErases.isEmpty())
		{
			send(new RsPaintStrokeErase(new ArrayList<>(pendingErases)));
		}
	}

	/** Sends the strokes in chunks of at most MAX_POINTS_PER_MESSAGE points. Only the first chunk resets. */
	private void sendFullSync(List<Stroke> strokes)
	{
		boolean reset = true;
		List<SyncedStroke> chunk = new ArrayList<>();
		int chunkPoints = 0;
		for (Stroke stroke : strokes)
		{
			if (stroke.pts.isEmpty())
			{
				continue;
			}

			pendingPoints.remove(stroke);

			if (!chunk.isEmpty() && chunkPoints + stroke.pts.size() > MAX_POINTS_PER_MESSAGE)
			{
				send(new RsPaintCanvasSync(reset, chunk));
				reset = false;
				chunk = new ArrayList<>();
				chunkPoints = 0;
			}
			chunk.add(toSynced(stroke, stroke.pts));
			chunkPoints += stroke.pts.size();
		}

		// A reset goes out even when empty, so receivers drop old strokes.
		if (!chunk.isEmpty() || reset)
		{
			send(new RsPaintCanvasSync(reset, chunk));
		}
	}

	static SyncedStroke toSynced(Stroke stroke, List<StrokePoint> points)
	{
		return new SyncedStroke(stroke.id, stroke.color.getRGB(), stroke.strokeWidth, stroke.order, new ArrayList<>(points));
	}

	private boolean canSend()
	{
		return partyService.isInParty() && partyService.getLocalMember() != null;
	}

	private void send(PartyMessage message)
	{
		try
		{
			partyService.send(message);
		}
		catch (IllegalStateException e)
		{
			log.debug("Could not send party message {}", message, e);
		}
	}

	private void clearPending()
	{
		pendingPoints.clear();
		pendingErases.clear();
		fullSyncPending = false;
		canvasRequestPending = false;
	}

	/** Returns the sender's canvas, or null if the sender is the local player or not in the party. */
	private MemberCanvas canvasFor(PartyMemberMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == message.getMemberId())
		{
			return null;
		}
		if (partyService.getMemberById(message.getMemberId()) == null)
		{
			return null;
		}
		return canvases.computeIfAbsent(message.getMemberId(), k -> new MemberCanvas());
	}

	@Subscribe
	public void onRsPaintStrokeAppend(RsPaintStrokeAppend message)
	{
		clientThread.invokeLater(() -> {
			MemberCanvas canvas = canvasFor(message);
			if (canvas != null && message.getStrokes() != null)
			{
				message.getStrokes().forEach(canvas::append);
			}
		});
	}

	@Subscribe
	public void onRsPaintStrokeErase(RsPaintStrokeErase message)
	{
		clientThread.invokeLater(() -> {
			MemberCanvas canvas = canvasFor(message);
			if (canvas != null && message.getStrokeIds() != null)
			{
				message.getStrokeIds().forEach(canvas::remove);
			}
		});
	}

	@Subscribe
	public void onRsPaintCanvasSync(RsPaintCanvasSync message)
	{
		clientThread.invokeLater(() -> {
			MemberCanvas canvas = canvasFor(message);
			if (canvas == null)
			{
				return;
			}
			if (message.isReset())
			{
				canvas.clear();
			}
			if (message.getStrokes() != null)
			{
				message.getStrokes().forEach(canvas::replace);
			}
		});
	}

	@Subscribe
	public void onRsPaintCanvasClear(RsPaintCanvasClear message)
	{
		clientThread.invokeLater(() -> {
			MemberCanvas canvas = canvasFor(message);
			if (canvas != null)
			{
				canvas.clear();
			}
		});
	}

	@Subscribe
	public void onRsPaintCanvasRequest(RsPaintCanvasRequest message)
	{
		clientThread.invokeLater(() -> fullSyncPending = true);
	}

	// A joining member sends UserSync.
	@Subscribe
	public void onUserSync(UserSync message)
	{
		clientThread.invokeLater(() -> fullSyncPending = true);
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		clientThread.invokeLater(() -> {
			PartyMember local = partyService.getLocalMember();
			if (local != null && local.getMemberId() == event.getMemberId())
			{
				fullSyncPending = true;
			}
		});
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		clientThread.invokeLater(() -> canvases.remove(event.getMemberId()));
		hiddenMembers.remove(event.getMemberId());
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		clientThread.invokeLater(() -> {
			canvases.clear();
			clearPending();
		});
		hiddenMembers.clear();
	}

	private class MemberCanvas
	{
		final Map<Integer, List<Stroke>> strokesByRegion = new HashMap<>();
		final Map<String, Stroke> strokesById = new HashMap<>();

		void append(SyncedStroke synced)
		{
			Stroke stroke = strokesById.get(synced.getId());
			if (stroke == null)
			{
				add(synced);
			}
			else if (synced.getPoints() != null)
			{
				stroke.pts.addAll(synced.getPoints());
			}
		}

		void replace(SyncedStroke synced)
		{
			remove(synced.getId());
			add(synced);
		}

		void remove(String id)
		{
			Stroke stroke = strokesById.remove(id);
			if (stroke != null)
			{
				List<Stroke> strokes = strokesByRegion.get(stroke.primaryRegion());
				if (strokes != null)
				{
					strokes.remove(stroke);
				}
			}
		}

		void clear()
		{
			strokesByRegion.clear();
			strokesById.clear();
		}

		private void add(SyncedStroke synced)
		{
			if (synced.getId() == null || synced.getPoints() == null || synced.getPoints().isEmpty())
			{
				return;
			}

			Stroke stroke = new Stroke();
			stroke.id = synced.getId();
			stroke.color = new Color(synced.getColor(), true);
			// Do not trust the width from the message. A very large width is slow to draw.
			stroke.strokeWidth = Stroke.validWidth(synced.getStrokeWidth());
			stroke.order = clock.receive(synced.getOrder());
			stroke.pts.addAll(synced.getPoints());

			strokesById.put(stroke.id, stroke);
			strokesByRegion.computeIfAbsent(stroke.primaryRegion(), k -> new ArrayList<>()).add(stroke);
		}
	}
}
