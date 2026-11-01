package com.rspaintextended;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;

/** The side panel, styled after the Time Tracking plugin's panel. */
@Slf4j
@Singleton
public class RsPaintExtendedPanel extends PluginPanel
{
	// PartyMember uses this name until the party service knows the real name.
	private static final String UNKNOWN_NAME = "<unknown>";

	// The same switch as the plugin list's on/off toggle.
	private static final ImageIcon SWITCH_ON;
	private static final ImageIcon SWITCH_OFF;

	static
	{
		BufferedImage on = ImageUtil.loadImageResource(RsPaintExtendedPanel.class, "switcher_on.png");
		SWITCH_ON = new ImageIcon(on);
		SWITCH_OFF = new ImageIcon(ImageUtil.flipImage(ImageUtil.luminanceScale(ImageUtil.grayscaleImage(on), 0.61f), true, false));
	}

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private PartyService partyService;
	@Inject private PartyCanvasSync partySync;
	@Inject private RsPaintExtendedOverlay overlay;
	@Inject private DrawingFiles drawingFiles;

	private final JTextArea partyStatus = new WrappingTextArea();
	private final JPanel memberList = new JPanel(new DynamicGridLayout(0, 1, 0, 0));

	// Client thread only.
	private List<MemberRow> shownRows = null;
	// Swing thread only.
	private File lastDirectory = null;

	@Value
	private static class MemberRow
	{
		long memberId;
		String name;
		boolean local;
	}

	@Inject
	RsPaintExtendedPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		partyStatus.setFont(FontManager.getRunescapeSmallFont());
		partyStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		partyStatus.setLineWrap(true);
		partyStatus.setWrapStyleWord(true);
		partyStatus.setEditable(false);
		partyStatus.setFocusable(false);
		partyStatus.setHighlighter(null);
		partyStatus.setOpaque(false);
		partyStatus.setBorder(null);

		memberList.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		display.setBorder(new EmptyBorder(10, 10, 8, 10));
		display.add(partyStatus, BorderLayout.NORTH);
		display.add(memberList, BorderLayout.CENTER);
		display.add(createButtons(), BorderLayout.SOUTH);

		JPanel wrapper = new ViewportWidthPanel();
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(display, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(wrapper);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
		scroll.getVerticalScrollBar().setBorder(new EmptyBorder(0, 9, 0, 0));
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setBorder(null);

		add(scroll, BorderLayout.CENTER);
	}

	/** Builds the rows again on the Swing thread if the party members changed. */
	void refresh()
	{
		clientThread.invokeLater(() -> {
			List<MemberRow> rows = collectRows();
			if (rows.equals(shownRows))
			{
				return;
			}
			shownRows = rows;
			boolean inParty = partyService.isInParty();
			SwingUtilities.invokeLater(() -> rebuild(rows, inParty));
		});
	}

	/** Makes the next refresh build all rows again. */
	void reset()
	{
		clientThread.invokeLater(() -> shownRows = null);
	}

	private List<MemberRow> collectRows()
	{
		List<MemberRow> rows = new ArrayList<>();
		PartyMember local = partyService.getLocalMember();
		rows.add(new MemberRow(local != null ? local.getMemberId() : -1, localName(), true));

		if (partyService.isInParty())
		{
			for (PartyMember member : partyService.getMembers())
			{
				if (local == null || member.getMemberId() != local.getMemberId())
				{
					rows.add(new MemberRow(member.getMemberId(), memberName(member), false));
				}
			}
		}
		return rows;
	}

	private String localName()
	{
		Player player = client.getLocalPlayer();
		return player != null && player.getName() != null ? player.getName() : "You";
	}

	private static String memberName(PartyMember member)
	{
		String name = member.getDisplayName();
		return name == null || name.isEmpty() || UNKNOWN_NAME.equals(name) ? "Unknown member" : name;
	}

	private void rebuild(List<MemberRow> rows, boolean inParty)
	{
		partyStatus.setText(inParty
			? "Party members can see your drawings."
			: "Join a party in the Party plugin to share your drawings.");

		memberList.removeAll();
		for (MemberRow row : rows)
		{
			memberList.add(createRow(row));
		}
		memberList.revalidate();
		memberList.repaint();
	}

	private JPanel createRow(MemberRow row)
	{
		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setBorder(new EmptyBorder(4, 4, 4, 0));

		JLabel name = new JShadowedLabel(row.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		text.add(name);

		JLabel role = new JLabel(row.isLocal() ? "Your drawings" : "Party member");
		role.setFont(FontManager.getRunescapeSmallFont());
		role.setForeground(Color.GRAY);
		text.add(role);

		JToggleButton show = new JToggleButton();
		show.setIcon(SWITCH_OFF);
		show.setSelectedIcon(SWITCH_ON);
		SwingUtil.removeButtonDecorations(show);
		show.setPreferredSize(new Dimension(25, 15));
		SwingUtil.addModalTooltip(show, "Hide drawings", "Show drawings");
		if (row.isLocal())
		{
			show.setSelected(overlay.isShowOwnStrokes());
			show.addActionListener(e -> overlay.setShowOwnStrokes(show.isSelected()));
		}
		else
		{
			show.setSelected(partySync.isMemberVisible(row.getMemberId()));
			show.addActionListener(e -> partySync.setMemberVisible(row.getMemberId(), show.isSelected()));
		}

		JPanel tile = new JPanel(new BorderLayout());
		tile.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tile.setBorder(new EmptyBorder(7, 7, 6, 10));
		tile.add(text, BorderLayout.CENTER);
		JPanel showWrapper = new JPanel(new GridBagLayout());
		showWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		GridBagConstraints exportConstraints = new GridBagConstraints();
		exportConstraints.insets = new Insets(0, 0, 0, 8);
		showWrapper.add(createExportLink(row), exportConstraints);
		showWrapper.add(show);
		tile.add(showWrapper, BorderLayout.EAST);

		JPanel container = new JPanel(new BorderLayout());
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setBorder(new EmptyBorder(7, 0, 0, 0));
		container.add(tile, BorderLayout.CENTER);
		return container;
	}

	private JLabel createExportLink(MemberRow row)
	{
		JLabel link = new JLabel("Export");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(Color.GRAY);
		link.setToolTipText(row.isLocal() ? "Save your drawings to a file." : "Save the drawings of " + row.getName() + " to a file.");
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (row.isLocal())
				{
					exportDrawings(row.getName(), overlay::ownStrokes);
				}
				else
				{
					exportDrawings(row.getName(), () -> partySync.memberStrokes(row.getMemberId()));
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(Color.GRAY);
			}
		});
		return link;
	}

	private JPanel createButtons()
	{
		JPanel buttons = new JPanel(new DynamicGridLayout(0, 1, 0, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(createButton("Import drawings",
			"Add the drawings in a file to your drawings. Party members see them too.", this::importDrawings));
		buttons.add(createButton("Export shown drawings",
			"Save the drawings of all players whose switch is on to one file.", this::exportShownDrawings));
		buttons.add(createButton("Reset canvas", "Delete all of your drawings.", this::confirmReset));
		return buttons;
	}

	private JPanel createButton(String text, String tooltip, Runnable action)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);

		JPanel button = new JPanel(new BorderLayout());
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(new EmptyBorder(8, 0, 8, 0));
		button.setToolTipText(tooltip);
		button.add(label, BorderLayout.CENTER);
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		JPanel container = new JPanel(new BorderLayout());
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setBorder(new EmptyBorder(10, 0, 0, 0));
		container.add(button, BorderLayout.CENTER);
		return container;
	}

	private void exportShownDrawings()
	{
		exportDrawings("party", () -> {
			List<SyncedStroke> strokes = new ArrayList<>();
			if (overlay.isShowOwnStrokes())
			{
				strokes.addAll(overlay.ownStrokes());
			}
			strokes.addAll(partySync.visibleMemberStrokes());
			return strokes;
		});
	}

	/** Collects the strokes on the client thread, then asks for a file on the Swing thread. */
	private void exportDrawings(String name, Supplier<List<SyncedStroke>> collect)
	{
		clientThread.invokeLater(() -> {
			List<SyncedStroke> strokes = collect.get();
			SwingUtilities.invokeLater(() -> saveToFile(name, strokes));
		});
	}

	private void saveToFile(String name, List<SyncedStroke> strokes)
	{
		if (strokes.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "There are no drawings to export.",
				"Export drawings", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JFileChooser chooser = createFileChooser("Export drawings");
		chooser.setSelectedFile(new File(chooser.getCurrentDirectory(),
			name.replaceAll("[^A-Za-z0-9 _-]", "_") + " drawings." + DrawingFiles.EXTENSION));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}

		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith("." + DrawingFiles.EXTENSION))
		{
			file = new File(file.getParentFile(), file.getName() + "." + DrawingFiles.EXTENSION);
		}
		lastDirectory = file.getParentFile();

		if (file.exists() && JOptionPane.showConfirmDialog(this,
			file.getName() + " already exists. Do you want to replace it?",
			"Export drawings", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
		{
			return;
		}

		try
		{
			drawingFiles.write(file, strokes);
		}
		catch (IOException e)
		{
			log.warn("Could not export drawings to {}", file, e);
			JOptionPane.showMessageDialog(this, "Could not save the file: " + e.getMessage(),
				"Export drawings", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importDrawings()
	{
		JFileChooser chooser = createFileChooser("Import drawings");
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}

		File file = chooser.getSelectedFile();
		lastDirectory = file.getParentFile();

		List<SyncedStroke> strokes;
		try
		{
			strokes = drawingFiles.read(file);
		}
		catch (IOException e)
		{
			log.warn("Could not import drawings from {}", file, e);
			JOptionPane.showMessageDialog(this, "Could not read the file: " + e.getMessage(),
				"Import drawings", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (strokes.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "The file has no drawings.",
				"Import drawings", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		clientThread.invokeLater(() -> overlay.importStrokes(strokes));
		JOptionPane.showMessageDialog(this,
			"Added " + strokes.size() + (strokes.size() == 1 ? " stroke" : " strokes") + " to your drawings."
				+ (overlay.isShowOwnStrokes() ? "" : " Turn on your switch to see them."),
			"Import drawings", JOptionPane.INFORMATION_MESSAGE);
	}

	private JFileChooser createFileChooser(String title)
	{
		JFileChooser chooser = new JFileChooser(lastDirectory != null ? lastDirectory : RuneLite.RUNELITE_DIR);
		chooser.setDialogTitle(title);
		chooser.setFileFilter(new FileNameExtensionFilter(
			"Drawing files (*." + DrawingFiles.EXTENSION + ")", DrawingFiles.EXTENSION));
		return chooser;
	}

	private void confirmReset()
	{
		int result = JOptionPane.showConfirmDialog(this,
			"This deletes all of your drawings. You cannot undo this.",
			"Reset canvas", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (result == JOptionPane.OK_OPTION)
		{
			clientThread.invokeLater(overlay::clearCanvas);
		}
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		refresh();
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		refresh();
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		refresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		refresh();
	}

	// Member names can arrive after the member joins.
	@Subscribe
	public void onGameTick(GameTick event)
	{
		refresh();
	}

	/** Measures its wrapped height at the parent's width, not the width from the last layout. */
	private static class WrappingTextArea extends JTextArea
	{
		@Override
		public Dimension getPreferredSize()
		{
			Container parent = getParent();
			int width;
			if (parent != null && parent.getWidth() > 0)
			{
				Insets insets = parent.getInsets();
				width = parent.getWidth() - insets.left - insets.right;
			}
			else
			{
				// Before the first layout: the panel width minus the 10 pixel side borders.
				width = PluginPanel.PANEL_WIDTH - 20;
			}

			if (width > 0 && width != getWidth())
			{
				setSize(width, Short.MAX_VALUE);
			}
			return super.getPreferredSize();
		}
	}

	/** Takes the width of the scroll pane's viewport, so its content wraps instead of overflowing. */
	private static class ViewportWidthPanel extends JPanel implements Scrollable
	{
		ViewportWidthPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			// Fill the viewport when the content is shorter than it, and scroll when it is taller.
			return getParent() != null && getParent().getHeight() > getPreferredSize().height;
		}
	}

	static BufferedImage createIcon()
	{
		BufferedImage brush = ImageUtil.loadImageResource(RsPaintExtendedPanel.class, "paintbrush.png");
		return ImageUtil.resizeCanvas(ImageUtil.resizeImage(brush, 16, 16, true), 16, 16);
	}
}
