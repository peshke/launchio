package io.github.peshke.launchio;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The TV's pass-through inputs as the Inputs menu lists them; the dock's input tiles and Settings use the same list. */
final class Inputs {

    static final class Input {
        final String id;
        /** The menu's row: "PlayStation 5 · HDMI 1", "MacBook · HDMI 2", "HDMI 3". */
        final String label;
        /** The short name a dock tile shows: the device, the name given in Settings > Inputs, or the port. */
        final String name;
        final boolean hdmi;
        /** TvInputManager.INPUT_STATE_*, or -1 when the TV cannot be trusted to say. */
        final int state;

        Input(String id, String label, String name, boolean hdmi, int state) {
            this.id = id;
            this.label = label;
            this.name = name;
            this.hdmi = hdmi;
            this.state = state;
        }
    }

    private Inputs() {}

    static List<Input> list(Context ctx) {
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        return tim == null ? new ArrayList<>() : list(ctx, tim);
    }

    static List<Input> list(Context ctx, TvInputManager tim) {
        List<Input> out = new ArrayList<>();
        List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
        // HDMI ports first in port order, then AV and the rest.
        Collections.sort(inputs, (a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return String.valueOf(a.loadLabel(ctx)).compareToIgnoreCase(String.valueOf(b.loadLabel(ctx)));
        });
        // An HDMI-CEC device (an Apple TV, a soundbar) gets an input of its own whose parent is the
        // port it sits on, so the port and the device would each get a row (Vestel MT7332: "HDMI 1"
        // and "HDMI Apple TV"). The device takes its port's row instead, and a bare port is listed
        // only when nothing on it speaks CEC.
        Set<String> inputIds = new HashSet<>();
        for (TvInputInfo info : inputs) inputIds.add(info.getId());
        Map<String, List<TvInputInfo>> cecDevices = new HashMap<>();
        for (TvInputInfo info : inputs) {
            String parent = info.getParentId();
            if (parent == null || !inputIds.contains(parent)) continue;
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            List<TvInputInfo> onPort = cecDevices.get(parent);
            if (onPort == null) cecDevices.put(parent, onPort = new ArrayList<>());
            onPort.add(info);
        }
        // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
        // so drop the HDMI status then. A TV with every port really in use loses it too.
        int hdmiPorts = 0, hdmiConnected = 0;
        for (TvInputInfo info : inputs) {
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
            hdmiPorts++;
            if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
        }
        boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
        for (TvInputInfo info : inputs) {
            // Inputs hidden in the TV's Settings > Inputs stay out of the menu too.
            if (!info.isPassthroughInput() || info.isHidden(ctx)) continue;
            // A CEC device's row is added with its port, below.
            if (info.getParentId() != null && inputIds.contains(info.getParentId())) continue;
            CharSequence l = info.loadLabel(ctx);
            String label = l == null ? info.getId() : l.toString();
            boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            CharSequence custom = info.loadCustomLabel(ctx);
            boolean named = custom != null && custom.length() > 0 && !custom.toString().equals(label);
            List<TvInputInfo> devices = cecDevices.get(info.getId());
            if (devices != null) {
                // "Apple TV · HDMI 1": the device's own name (or the one given to it or to its port in
                // Settings > Inputs), then the port. Opening the device's input also makes it the CEC
                // active source, which wakes it.
                for (TvInputInfo device : devices) {
                    CharSequence name = device.loadCustomLabel(ctx);
                    if (name == null || name.length() == 0) name = named ? custom : device.loadLabel(ctx);
                    boolean hasName = name != null && name.length() > 0;
                    String deviceId = device.getId();
                    out.add(new Input(deviceId, hasName ? name + " · " + label : label, hasName ? name.toString() : label,
                            true, hdmiStateBogus ? -1 : tim.getInputState(deviceId)));
                }
                continue;
            }
            // Analog AV jacks have no hotplug detection, and some TVs report them as always connected
            // (TCL Smart TV Pro G03 says "Connected" with nothing plugged in), so AV rows show no state.
            String id = info.getId();
            out.add(new Input(id, named ? custom + " · " + label : label, named ? custom.toString() : label,
                    hdmi, !hdmi || hdmiStateBogus ? -1 : tim.getInputState(id)));
        }
        return out;
    }
}
