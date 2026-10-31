package com.rspaintextended;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Sends complete strokes from the sender's canvas.
 * If reset is true, the receiver removes all of the sender's strokes before it adds these strokes.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class RsPaintCanvasSync extends PartyMemberMessage
{
	boolean reset;
	List<SyncedStroke> strokes;
}
