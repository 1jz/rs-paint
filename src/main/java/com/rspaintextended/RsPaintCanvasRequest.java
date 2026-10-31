package com.rspaintextended;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Asks all party members to send their canvas again. */
@Value
@EqualsAndHashCode(callSuper = true)
public class RsPaintCanvasRequest extends PartyMemberMessage
{
}
