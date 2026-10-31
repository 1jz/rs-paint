package com.rspaintextended;

import com.rspaintextended.RsPaintExtendedPlugin.StrokePoint;
import java.util.List;
import lombok.Value;

/** A stroke, or a part of a stroke, that is sent to party members. */
@Value
public class SyncedStroke
{
	String id;
	int color;
	double strokeWidth;
	List<StrokePoint> points;
}
