package com.rspaintextended;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Adds new points to the end of strokes. The receiver makes the stroke if it does not have it yet. */
@Value
@EqualsAndHashCode(callSuper = true)
public class RsPaintStrokeAppend extends PartyMemberMessage
{
	List<SyncedStroke> strokes;
}
