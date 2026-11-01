package com.rspaintextended;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rspaintextended.RsPaintExtendedPlugin.Stroke;
import com.rspaintextended.RsPaintExtendedPlugin.StrokePoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.FocusChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;

@Singleton
public class RsPaintExtendedOverlay extends Overlay implements MouseListener
{
	@Inject Client client;
	@Inject private RsPaintExtendedConfig config;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	@Inject private ColorPickerManager colorPickerManager;
	@Inject private PartyCanvasSync partySync;

	@Getter @Setter private volatile boolean showOwnStrokes = true;

	static class pf extends Point2D.Float { pf(float x, float y) { super(x, y);} }
	static pf pf(float x, float y) { return new pf(x, y); }

	private static final Type COLOR_LIST_TYPE = new TypeToken<List<Color>>(){}.getType();

	// Search order around the selected scene tile for the tile under the mouse.
	private static final int[] TILE_X_OFFSETS = {0, 1, 1, 0, -1, -1, -1, 0, 1,
		2, 2, 2, 1, 0, -1, -2, -2, -2, -2, -2, -1, 0, -1, 2, 2};
	private static final int[] TILE_Y_OFFSETS = {0, 0, 1, 1, 1, 0, -1, -1, -1,
		0, 1, 2, 2, 2, 2, 2, 1, 0, -1, -2, -2, -2, -2, -2, -1};

	static final Color DEFAULT_COLOR = Color.LIGHT_GRAY;

	// Two columns of color slots. The first column starts with the Paint palette.
	static final int SLOTS_PER_COLUMN = 10;
	static final int COLOR_SLOTS = SLOTS_PER_COLUMN * 2;
	static final Color[] DEFAULT_PALETTE = {
		new Color(0x000000), // black
		new Color(0xFFFFFF), // white
		new Color(0x7F7F7F), // gray
		new Color(0xED1C24), // red
		new Color(0xFF7F27), // orange
		new Color(0xFFF200), // yellow
		new Color(0x22B14C), // green
		new Color(0x00A2E8), // turquoise
		new Color(0x3F48CC), // indigo
		new Color(0xA349A4), // purple
	};

	@Inject
	public RsPaintExtendedOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(Overlay.PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	// The local player's strokes by region. Memory only, so they last one client session.
	final Map<Integer, List<Stroke>> strokesByRegion = new HashMap<>();
	Stroke currentStroke = null;

	List<Color> colors = new ArrayList<>();
	int selectedColor = 0;

	private Color selectedColor()
	{
		Color color = colors.get(selectedColor);
		return color != null ? color : DEFAULT_COLOR;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Point mouse = client.getMouseCanvasPosition();
		int mx = mouse.getX();
		int my = mouse.getY();

		if (!drawing)
		{
			currentStroke = null;
		}

		boolean showUi = eraseMode || drawMode || drawing || erasing;
		boolean overUi = showUi && getMousedOverToolbarButton(mx, my) != -1;

		Tile tile = drawMode || drawing ? client.getTopLevelWorldView().getSelectedSceneTile() : null;
		pf ground = null;

		if (tile != null)
		{
			Polygon poly = null;
			int tilex = -1;
			int tiley = -1;
			for (int i = 0; i < TILE_X_OFFSETS.length; i++)
			{
				LocalPoint plus = tile.getLocalLocation().plus(TILE_X_OFFSETS[i] * 128, TILE_Y_OFFSETS[i] * 128);
				Polygon test = Perspective.getCanvasTilePoly(client, plus);
				if (test != null && test.contains(mx, my))
				{
					poly = test;
					tilex = tile.getWorldLocation().getX() + TILE_X_OFFSETS[i];
					tiley = tile.getWorldLocation().getY() + TILE_Y_OFFSETS[i];
					break;
				}
			}

			if (poly != null)
			{
				int
					x0 = poly.xpoints[0],
					y0 = poly.ypoints[0],
					x1 = poly.xpoints[1],
					y1 = poly.ypoints[1],
					x2 = poly.xpoints[2],
					y2 = poly.ypoints[2],
					x3 = poly.xpoints[3],
					y3 = poly.ypoints[3];

				pf sub = getBarycentre(mx, my, x0, y0, x1, y1, x2, y2, x3, y3);
				ground = projectToScreen(sub.x, sub.y, x0, y0, x1, y1, x2, y2, x3, y3);

				if (drawing) {
					if (currentStroke == null) {
						currentStroke = new Stroke();
						currentStroke.color = selectedColor();
						currentStroke.strokeWidth = 1.0f;
						strokesByRegion.computeIfAbsent(new WorldPoint(tilex, tiley, 0).getRegionID(), k -> new ArrayList<>()).add(currentStroke);
					}
					StrokePoint point = new StrokePoint(tilex, tiley, sub.x, sub.y);
					List<StrokePoint> pts = currentStroke.pts;
					if (pts.isEmpty() || !pts.get(pts.size() - 1).equals(point)) {
						pts.add(point);
						partySync.onPointAdded(currentStroke, point);
					}
				}
			}
		}

		drawStrokes(graphics, mx, my);

		if (showUi) {
			if (!overUi) {
				if (eraseMode || erasing) {
					graphics.setColor(Color.PINK);
					graphics.draw(new Ellipse2D.Float(mx - 10, my - 10, 20, 20));
				} else if ((drawMode || drawing) && ground != null) {
					graphics.setColor(selectedColor());
					graphics.drawRect((int) ground.x - 1, (int) ground.y - 1, 2, 2);
				}
			}

			for (int i = 0; i < buttons.size(); i++)
			{
				Rectangle rectangle = toolbarButtonClickbox(i);
				boolean mouseOver = rectangle.contains(mx, my);
				buttons.get(i).draw(graphics, toolbarButtonVisibleBounds(i), mouseOver);
			}
		}

		return null;
	}

	List<ToolbarButton> buttons = new ArrayList<>();
	{
		buttons.add(new ToolbarButton() {
			@Override public void draw(Graphics2D g, Rectangle r, boolean mouseOver) {
				g.setColor(Color.LIGHT_GRAY);
				g.drawLine(r.x + r.width / 2, r.y + 3, r.x + r.width / 2, r.y + r.height - 3);
				g.drawLine(r.x + 3, r.y + r.height / 2, r.x + r.width - 3, r.y + r.height / 2);
				super.draw(g, r, mouseOver);
			}
			@Override public void addMenuEntries() {
				client.getMenu().createMenuEntry(-1).setOption("New color").onClick(me -> pickColor(-1));
			}
		});
		// Reset palette: a circular arrow.
		buttons.add(new ToolbarButton() {
			@Override public void draw(Graphics2D g, Rectangle r, boolean mouseOver) {
				g.setColor(Color.LIGHT_GRAY);
				int cx = r.x + r.width / 2;
				int top = r.y + 3;
				g.drawArc(r.x + 3, top, r.width - 6, r.height - 6, 90, -270);
				g.drawLine(cx - 1, top, cx + 2, top - 2);
				g.drawLine(cx - 1, top, cx + 2, top + 2);
				super.draw(g, r, mouseOver);
			}
			@Override public void addMenuEntries() {
				client.getMenu().createMenuEntry(-1).setOption("Reset").setTarget("palette").onClick(me -> useDefaultPalette());
			}
		});
		for (int i = 0; i < COLOR_SLOTS; i++) {
			buttons.add(new ColorToolbarButton(i));
		}
	}

	class ToolbarButton {
		public void draw(Graphics2D g, Rectangle r, boolean mouseOver) {
			g.setColor(mouseOver ? Color.WHITE : Color.LIGHT_GRAY);
			g.draw(r);
		}

		public void addMenuEntries() { }
		public void onDrag(ToolbarButton b) { }
	}

	@RequiredArgsConstructor
	class ColorToolbarButton extends ToolbarButton {
		final int colorIndex;
		@Override public void draw(Graphics2D g, Rectangle r, boolean mouseOver) {
			Color color = colorIndex < colors.size() ? colors.get(colorIndex) : null;
			if (colorIndex == selectedColor) r.grow(2, 2);
			if (color != null) {
				g.setColor(color);
				g.fill(r);
			}
			g.setColor((mouseOver || colorIndex == selectedColor) ? Color.WHITE : Color.GRAY);
			g.draw(r);
		}

		@Override public void addMenuEntries() {
			Color color = colors.get(colorIndex);
			if (color != null) {
				client.getMenu().createMenuEntry(1).setOption(ColorUtil.wrapWithColorTag("Select color", color)).onClick(me -> selectedColor = colorIndex);
			}
			client.getMenu().createMenuEntry(1).setOption("Choose ").setTarget("new color").onClick(me -> pickColor(colorIndex));
		}

		@Override public void onDrag(ToolbarButton b) {
			if (b instanceof ColorToolbarButton) {
				int otherIndex = ((ColorToolbarButton) b).colorIndex;
				Color mine = colors.get(colorIndex);
				Color other = colors.get(otherIndex);
				colors.set(colorIndex, other);
				colors.set(otherIndex, mine);
				saveColors();
			}
		}
	}

	/** If index is -1, the new color goes in slot 0 and the other colors move down one slot. */
	public void pickColor(int index) {
		Color color = colors.get(index == -1 ? selectedColor : index);
		SwingUtilities.invokeLater(() ->
		{
			RuneliteColorPicker colorPicker = colorPickerManager.create(client,
				color != null ? color : Color.WHITE, "Paint color", true);
			colorPicker.setOnClose(c -> {
				if (index == -1) {
					for (int i = colors.size() - 1; i > 0; i--) {
						colors.set(i, colors.get(i - 1));
					}
				}
				colors.set(index == -1 ? 0 : index, c);
				saveColors();
			});
			colorPicker.setVisible(true);
		});
	}

	private void saveColors()
	{
		configManager.setConfiguration(RsPaintExtendedConfig.GROUP, "drawColors", gson.toJson(colors));
	}

	@Subscribe public void onClientTick(ClientTick e) {
		partySync.flush(strokesByRegion);

		if (!drawMode && !eraseMode && !drawing && !erasing || client.isMenuOpen()) return;

		Point mouse = client.getMouseCanvasPosition();
		int button = getMousedOverToolbarButton(mouse.getX(), mouse.getY());
		if (button != -1) {
			client.getMenu().setMenuEntries(Arrays.stream(client.getMenu().getMenuEntries()).filter(me -> me.getType() == MenuAction.CANCEL).toArray(MenuEntry[]::new));
			buttons.get(button).addMenuEntries();
			return;
		}
		if ((drawMode || eraseMode) && Arrays.stream(client.getMenu().getMenuEntries()).anyMatch(entry -> entry.getType() == MenuAction.WALK)) {
			client.getMenu().createMenuEntry(-1).setOption(drawMode ? ColorUtil.wrapWithColorTag("Draw", selectedColor()) : "Erase").setType(MenuAction.CC_OP);
			drawable = true;
		} else {
			drawable = false;
		}
	}

	private pf projectToScreen(float subx, float suby, int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3)
	{
		float screenx, screeny;
		if (subx + suby <= 1)
		{
			screenx = (int) (x0 + (x1 - x0) * subx + (x3 - x0) * suby);
			screeny = (int) (y0 + (y1 - y0) * subx + (y3 - y0) * suby);
		}
		else
		{
			subx = 1 - subx;
			suby = 1 - suby;
			screenx = (int) (x2 + (x3 - x2) * subx + (x1 - x2) * suby);
			screeny = (int) (y2 + (y3 - y2) * subx + (y1 - y2) * suby);
		}
		return pf(screenx, screeny);
	}

	private pf getBarycentre(int mx, int my, int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3)
	{
		// Try the triangle made by corners 0, 1, 3 first, then the one made by corners 1, 2, 3.
		float
			area13p = triangleArea(x1, y1, x3, y3, mx, my),
			area03p = triangleArea(x0, y0, x3, y3, mx, my),
			area01p = triangleArea(x0, y0, x1, y1, mx, my),
			area013 = triangleArea(x0, y0, x1, y1, x3, y3);
		float
			swb0 = area13p / area013,
			swb1 = area03p / area013,
			swb3 = area01p / area013;
		if (swb0 + swb1 + swb3 > 1f)
		{
			float
				area23p = triangleArea(x2, y2, x3, y3, mx, my),
				area12p = triangleArea(x1, y1, x2, y2, mx, my),
				area123 = triangleArea(x1, y1, x2, y2, x3, y3);
			return pf(1 - area12p / area123, 1 - area23p / area123);
		}
		return pf(swb1, swb3);
	}

	/** Deletes all of the local player's strokes and tells party members to do the same. */
	public void clearCanvas()
	{
		currentStroke = null;
		strokesByRegion.clear();
		partySync.onCanvasCleared();
	}

	/** Returns a copy of the local player's strokes. Client thread only. */
	List<SyncedStroke> ownStrokes()
	{
		List<SyncedStroke> result = new ArrayList<>();
		for (List<Stroke> strokes : strokesByRegion.values())
		{
			for (Stroke stroke : strokes)
			{
				if (!stroke.pts.isEmpty())
				{
					result.add(PartyCanvasSync.toSynced(stroke, stroke.pts));
				}
			}
		}
		return result;
	}

	/** Adds the strokes to the local player's canvas with new ids, and sends them to the party. Client thread only. */
	void importStrokes(List<SyncedStroke> strokes)
	{
		for (SyncedStroke synced : strokes)
		{
			if (synced.getPoints() == null || synced.getPoints().isEmpty())
			{
				continue;
			}
			Stroke stroke = new Stroke();
			stroke.color = new Color(synced.getColor(), true);
			stroke.strokeWidth = synced.getStrokeWidth();
			stroke.pts.addAll(synced.getPoints());
			strokesByRegion.computeIfAbsent(stroke.primaryRegion(), k -> new ArrayList<>()).add(stroke);
		}
		partySync.requestFullSync();
	}

	private void drawStrokes(Graphics2D graphics, int mx, int my)
	{
		WorldView worldView = client.getTopLevelWorldView();
		int[] regions = worldView.getMapRegions();
		if (regions == null)
		{
			return;
		}

		pf eraseAt = erasing ? pf(mx, my) : null;
		for (int region : regions)
		{
			List<Stroke> strokes = strokesByRegion.get(region);
			if (strokes == null)
			{
				continue;
			}

			List<Stroke> toErase = null;
			for (Stroke s : strokes)
			{
				if (!showOwnStrokes && s != currentStroke)
				{
					continue;
				}
				if (drawStroke(graphics, worldView, s, eraseAt))
				{
					if (toErase == null)
					{
						toErase = new ArrayList<>();
					}
					toErase.add(s);
				}
			}

			if (toErase != null) {
				strokes.removeAll(toErase);
				partySync.onStrokesErased(toErase);
			}
		}

		partySync.forEachVisibleStroke(regions, s -> drawStroke(graphics, worldView, s, null));
	}

	private BasicStroke basicStroke = new BasicStroke(1f);

	private BasicStroke basicStroke(double width)
	{
		if (basicStroke.getLineWidth() != (float) width)
		{
			basicStroke = new BasicStroke((float) width);
		}
		return basicStroke;
	}

	/** Draws one stroke. If eraseAt is not null, returns true when the stroke is near that point. */
	private boolean drawStroke(Graphics2D graphics, WorldView worldView, Stroke s, pf eraseAt)
	{
		boolean hit = false;
		graphics.setColor(s.color);
		graphics.setStroke(basicStroke(s.strokeWidth));
		int lastx = -1, lasty = -1;

		// Consecutive points are often on the same tile, so project each tile once.
		int tileX = Integer.MIN_VALUE, tileY = Integer.MIN_VALUE;
		boolean inScene = false;
		Polygon poly = null;
		for (StrokePoint p : s.pts)
		{
			if (p.getX() != tileX || p.getY() != tileY)
			{
				tileX = p.getX();
				tileY = p.getY();
				LocalPoint lp = LocalPoint.fromWorld(worldView, tileX, tileY);
				inScene = lp != null;
				poly = inScene ? Perspective.getCanvasTilePoly(client, lp) : null;
			}

			if (!inScene)
			{
				continue;
			}
			if (poly == null)
			{
				lastx = lasty = -1;
				continue;
			}
			int
				x0 = poly.xpoints[0],
				y0 = poly.ypoints[0],
				x1 = poly.xpoints[1],
				y1 = poly.ypoints[1],
				x2 = poly.xpoints[2],
				y2 = poly.ypoints[2],
				x3 = poly.xpoints[3],
				y3 = poly.ypoints[3];
			pf screen = projectToScreen(p.getSubx(), p.getSuby(), x0, y0, x1, y1, x2, y2, x3, y3);
			int screenx = (int) screen.x, screeny = (int) screen.y;
			if (lastx != -1)
			{
				if (eraseAt != null && !hit && Line2D.ptSegDist(lastx, lasty, screenx, screeny, eraseAt.x, eraseAt.y) < 10)
				{
					hit = true;
				}
				graphics.drawLine(lastx, lasty, screenx, screeny);
			}
			lastx = screenx;
			lasty = screeny;
		}
		return hit;
	}

	private Rectangle toolbarButtonVisibleBounds(int i) {
		Rectangle r = toolbarButtonClickbox(i);
		r.grow(-1, -1);
		return r;
	}

	// "New color" and "Reset palette" make the top row. The color slots go below them.
	private static final int TOP_ROW_BUTTONS = 2;

	private Rectangle toolbarButtonClickbox(int i) {
		int column, row;
		if (i < TOP_ROW_BUTTONS) {
			column = i;
			row = 0;
		} else {
			column = (i - TOP_ROW_BUTTONS) / SLOTS_PER_COLUMN;
			row = (i - TOP_ROW_BUTTONS) % SLOTS_PER_COLUMN + 1;
		}
		return new Rectangle(2 + 20 * column, 40 + 20 * row, 16, 16);
	}

	private int getMousedOverToolbarButton(int mx, int my) {
		for (int i = 0; i < buttons.size(); i++)
		{
			if (toolbarButtonClickbox(i).contains(mx, my)) {
				return i;
			}
		}
		return -1;
	}

	boolean drawMode = false;
	boolean drawing = false;
	boolean eraseMode = false;
	boolean erasing = false;
	boolean drawable = false; // The last menu had a WALK entry.

	int toolbarButtonBeingDraggedIndex = -1;

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (e.getButton() != MouseEvent.BUTTON1 || client.isMenuOpen()) return e;

		toolbarButtonBeingDraggedIndex = getMousedOverToolbarButton(e.getX(), e.getY());
		if (toolbarButtonBeingDraggedIndex != -1) {
			return e;
		}

		if (drawable) {
			if (drawMode) drawing = true;
			else if (eraseMode) erasing = true;
		}

		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (e.getButton() != MouseEvent.BUTTON1) return e;

		int i = getMousedOverToolbarButton(e.getX(), e.getY());
		if (i != -1 && toolbarButtonBeingDraggedIndex != -1) {
			buttons.get(i).onDrag(buttons.get(toolbarButtonBeingDraggedIndex));
		}

		drawing = false;
		erasing = false;
		return e;
	}

	final HotkeyListener drawHotkey = new HotkeyListener(() -> config.drawKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			setMode(!drawMode, false);
		}
	};

	final HotkeyListener eraseHotkey = new HotkeyListener(() -> config.eraseKeybind())
	{
		@Override
		public void hotkeyPressed()
		{
			setMode(false, !eraseMode);
		}
	};

	private void setMode(boolean draw, boolean erase)
	{
		drawMode = draw;
		eraseMode = erase;
		drawing = false;
		erasing = false;
	}

	// The mouse button can be released outside the client.
	@Subscribe public void onFocusChanged(FocusChanged e) {
		if (!e.isFocused()) {
			drawing = false;
			erasing = false;
		}
	}

	@Override public MouseEvent mouseClicked(MouseEvent e) { return e; }
	@Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
	@Override public MouseEvent mouseExited(MouseEvent e) { return e; }
	@Override public MouseEvent mouseDragged(MouseEvent e) { return e; }
	@Override public MouseEvent mouseMoved(MouseEvent e) { return e; }

	public void startUp() {
		showOwnStrokes = true;

		String configuration = configManager.getConfiguration(RsPaintExtendedConfig.GROUP, "drawColors");
		List<Color> saved = configuration != null ? gson.fromJson(configuration, COLOR_LIST_TYPE) : null;
		if (saved == null || isOldDefaultPalette(saved)) {
			useDefaultPalette();
			return;
		}
		while (saved.size() < COLOR_SLOTS) {
			saved.add(null);
		}
		colors = saved;
	}

	private void useDefaultPalette() {
		colors = new ArrayList<>(Arrays.asList(DEFAULT_PALETTE));
		while (colors.size() < COLOR_SLOTS) {
			colors.add(null);
		}
		saveColors();
	}

	/** True if the saved palette is the old one-color default, so the player never chose a color. */
	private static boolean isOldDefaultPalette(List<Color> saved) {
		for (int i = 0; i < saved.size(); i++) {
			Color color = saved.get(i);
			if (color != null && !(i == 0 && color.equals(DEFAULT_COLOR))) {
				return false;
			}
		}
		return true;
	}

	static float triangleArea(int x1, int y1, int x2, int y2, int x3, int y3)
	{
		return Math.abs((x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)) / 2);
	}
}
