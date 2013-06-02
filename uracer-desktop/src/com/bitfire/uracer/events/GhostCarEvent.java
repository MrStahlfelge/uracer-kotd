
package com.bitfire.uracer.events;

public final class GhostCarEvent extends Event<GhostCarEvent.Type, GhostCarEvent.Order, GhostCarEvent.Listener> {
	public enum Type {
		onGhostFadingOut
	}

	public enum Order {
		Default
	}

	public interface Listener extends Event.Listener<Type, Order> {
		@Override
		public void handle (Object source, Type type, Order order);
	}

	public GhostCarEvent () {
		super(Type.class, Order.class);
	}
}
