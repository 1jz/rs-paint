package com.rspaintextended;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.rspaintextended.RsPaintExtendedPlugin.StrokePoint;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * A stroke, or a part of a stroke, that is sent to party members or saved in a drawing file.
 * The JSON names are one letter, and the points are one encoded string, because a full sync can have 1000 strokes.
 */
@Value
public class SyncedStroke
{
	/** From the stroke that draws first, at the bottom, to the stroke that draws last, on top. */
	static final Comparator<SyncedStroke> DRAW_ORDER = Comparator.comparingLong(SyncedStroke::getOrder)
		.thenComparing(SyncedStroke::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

	@SerializedName("i")
	String id;
	@SerializedName("c")
	int color;
	@SerializedName("w")
	double strokeWidth;
	/** The draw order from StrokeClock. It is 0 if the sender has an old version of the plugin. */
	@SerializedName("o")
	long order;
	@SerializedName("p")
	@JsonAdapter(StrokeCodec.PointsAdapter.class)
	List<StrokePoint> points;
}
