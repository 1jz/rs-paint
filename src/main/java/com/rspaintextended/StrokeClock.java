package com.rspaintextended;

import javax.inject.Singleton;

/**
 * Gives each stroke a draw order. A stroke with a higher order draws over a stroke with a lower order.
 *
 * <p>The order of a new stroke is the current time in milliseconds, but always higher than the order of
 * all strokes that this client already has. Thus a new stroke draws over all strokes that the player can see,
 * also when the clocks of party members are not the same. Party members send the order with each stroke,
 * so all clients draw the strokes in the same order.
 *
 * <p>Client thread only.
 */
@Singleton
class StrokeClock
{
	// Larger orders are not accepted from party members, so a bad value cannot make the clock overflow.
	private static final long MAX_ORDER = 1L << 53;

	private long last = 0;

	/** Returns the order for a new stroke. */
	long next()
	{
		last = Math.max(last + 1, System.currentTimeMillis());
		return last;
	}

	/**
	 * Returns the order to use for a stroke from a party member, and moves the clock past it.
	 * If the order is not valid, for example because the member has an old version of the plugin, returns a new order.
	 */
	long receive(long order)
	{
		if (order <= 0 || order >= MAX_ORDER)
		{
			return next();
		}
		last = Math.max(last, order);
		return order;
	}
}
