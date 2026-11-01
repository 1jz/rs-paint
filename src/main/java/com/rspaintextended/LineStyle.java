package com.rspaintextended;

import lombok.RequiredArgsConstructor;

/** How a stroke connects its points on the screen. It changes only the drawing, not the stored points. */
@RequiredArgsConstructor
public enum LineStyle
{
	STRAIGHT("Straight"),
	SMOOTH("Smooth"),
	THROUGH_POINTS("Smooth through points");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
