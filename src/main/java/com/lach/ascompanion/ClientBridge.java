package com.lach.ascompanion;

import com.lach.ascompanion.net.StockPayload;

/** Keeps client classes out of common code paths that also run on a dedicated server. */
public final class ClientBridge {
    private ClientBridge() {
    }

    public static void stock(StockPayload p) {
        com.lach.ascompanion.client.LensClient.onStock(p);
    }

    public static void startScan() {
        com.lach.ascompanion.client.LensClient.startScan();
    }

    public static void clear() {
        com.lach.ascompanion.client.LensClient.clear();
    }
}
