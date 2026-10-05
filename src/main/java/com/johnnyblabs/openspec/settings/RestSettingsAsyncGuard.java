package com.johnnyblabs.openspec.settings;

import java.util.EnumMap;

/** Owns settings callbacks across provider switches, repeat requests and panel disposal. */
final class RestSettingsAsyncGuard {
    enum Slot { KEY, CATALOG, TEST }
    record Ticket(long epoch, long sequence, Slot slot) { }
    private long epoch;
    private long sequence;
    private boolean disposed;
    private final EnumMap<Slot, Ticket> current = new EnumMap<>(Slot.class);

    synchronized void providerChanged() { epoch++; current.clear(); }
    synchronized Ticket start(Slot slot) {
        Ticket ticket = new Ticket(epoch, ++sequence, slot);
        current.put(slot, ticket);
        return ticket;
    }
    synchronized boolean isCurrent(Ticket ticket) {
        return !disposed && ticket != null && ticket.epoch() == epoch && ticket.equals(current.get(ticket.slot()));
    }
    /** Called on EDT by the panel; callback executes only while this request owns the slot. */
    synchronized boolean apply(Ticket ticket, Runnable callback) {
        if (!isCurrent(ticket)) return false;
        callback.run();
        return true;
    }
    synchronized void invalidate(Slot slot) { current.remove(slot); }
    synchronized void dispose() { disposed = true; current.clear(); }
}
