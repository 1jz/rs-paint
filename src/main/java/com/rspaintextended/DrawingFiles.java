package com.rspaintextended;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.rspaintextended.RsPaintExtendedPlugin.StrokePoint;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/** Reads and writes drawing files. A drawing file is JSON that holds a list of strokes. */
@Singleton
class DrawingFiles
{
	static final String EXTENSION = "json";

	private static final int FORMAT_VERSION = 1;
	private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

	@Inject private Gson gson;

	@Value
	private static class DrawingFile
	{
		int version;
		List<SyncedStroke> strokes;
	}

	void write(File file, List<SyncedStroke> strokes) throws IOException
	{
		try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))
		{
			gson.toJson(new DrawingFile(FORMAT_VERSION, strokes), writer);
		}
	}

	/** Returns the valid strokes in the file. Strokes without points are skipped. */
	List<SyncedStroke> read(File file) throws IOException
	{
		if (file.length() > MAX_FILE_BYTES)
		{
			throw new IOException("The file is too large.");
		}

		DrawingFile drawing;
		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			drawing = gson.fromJson(reader, DrawingFile.class);
		}
		catch (JsonParseException e)
		{
			throw new IOException("The file is not a drawing file.", e);
		}

		if (drawing == null || drawing.getStrokes() == null)
		{
			throw new IOException("The file is not a drawing file.");
		}
		if (drawing.getVersion() > FORMAT_VERSION)
		{
			throw new IOException("The file was made by a newer version of the plugin.");
		}

		List<SyncedStroke> strokes = new ArrayList<>();
		for (SyncedStroke stroke : drawing.getStrokes())
		{
			SyncedStroke clean = clean(stroke);
			if (clean != null)
			{
				strokes.add(clean);
			}
		}
		return strokes;
	}

	private static SyncedStroke clean(SyncedStroke stroke)
	{
		if (stroke == null || stroke.getPoints() == null)
		{
			return null;
		}

		List<StrokePoint> points = new ArrayList<>();
		for (StrokePoint point : stroke.getPoints())
		{
			if (point != null && Float.isFinite(point.getSubx()) && Float.isFinite(point.getSuby()))
			{
				points.add(new StrokePoint(point.getX(), point.getY(), clamp01(point.getSubx()), clamp01(point.getSuby())));
			}
		}
		if (points.isEmpty())
		{
			return null;
		}

		double width = RsPaintExtendedPlugin.Stroke.validWidth(stroke.getStrokeWidth());
		return new SyncedStroke(stroke.getId(), stroke.getColor(), width, points);
	}

	private static float clamp01(float value)
	{
		return Math.max(0f, Math.min(1f, value));
	}
}
