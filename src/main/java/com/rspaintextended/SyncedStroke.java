package com.rspaintextended;

import com.rspaintextended.RsPaintExtendedPlugin.StrokePoint;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/** A stroke, or a part of a stroke, that is sent to party members. */
@Value
public class SyncedStroke
{
	/** From the stroke that draws first, at the bottom, to the stroke that draws last, on top. */
	static final Comparator<SyncedStroke> DRAW_ORDER = Comparator.comparingLong(SyncedStroke::getOrder)
		.thenComparing(SyncedStroke::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

	String id;
	int color;
	double strokeWidth;
	/** The draw order from StrokeClock. It is 0 if the sender has an old version of the plugin. */
	long order;
	List<StrokePoint> points;
}
