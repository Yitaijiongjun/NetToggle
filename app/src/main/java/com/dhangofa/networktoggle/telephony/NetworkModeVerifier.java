package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.NetworkMode;

/** A returned setter/exit code is only dispatch success; require two stable readbacks. */
final class NetworkModeVerifier {
    interface Probe { NetworkModeReadback read(); }
    interface Pause { void await() throws InterruptedException; }

    static CommandResult verify(NetworkMode requested, CommandResult dispatch, Probe probe,
            int attempts, Pause pause) {
        NetworkModeReadback state = null;
        int consecutive = 0;
        for (int i = 0; i < attempts; i++) {
            // Probe immediately. Only wait between readbacks, never before the first one.
            try { if (i > 0) pause.await(); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return CommandResult.failed(dispatch.getCommand(), "Network verification interrupted");
            }
            state = probe.read();
            consecutive = state != null && state.matches(requested) ? consecutive + 1 : 0;
            if (consecutive >= 2) return CommandResult.completed(dispatch.getCommand(), 0,
                    dispatch.getStdout() + "\nVerified: " + state, dispatch.getStderr());
        }
        return CommandResult.failed(dispatch.getCommand(), "Setter dispatched but readback did not settle to "
                + requested + ". " + state + ". Dispatch: " + dispatch.getStdout());
    }
}
