package com.rspaintextended;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Removes strokes from the sender's canvas. */
@Value
@EqualsAndHashCode(callSuper = true)
public class RsPaintStrokeErase extends PartyMemberMessage
{
	List<String> strokeIds;
}
