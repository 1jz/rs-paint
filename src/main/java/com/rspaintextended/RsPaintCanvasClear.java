package com.rspaintextended;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Removes all strokes from the sender's canvas. */
@Value
@EqualsAndHashCode(callSuper = true)
public class RsPaintCanvasClear extends PartyMemberMessage
{
}
