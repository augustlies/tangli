package com.ecut.campus;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * 下拉快捷开关「烫梨拦截」：一键开关，不用进 App。
 * 只在下拉面板显示时由系统拉起，平时不占资源、不影响启动。
 */
public class DnsTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        render();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (DnsControl.running()) {
            DnsControl.stop(this);
        } else if (!DnsControl.start(this)) {
            // 还没给过 VPN 授权：进 App 走授权（targetSdk 33，Intent 版本在 Android 14 上仍可用）
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(AppActions.EXTRA_ACTION, "dns")
                    .putExtra(AppActions.EXTRA_VALUE, "start");
            startActivityAndCollapse(i);
            return;
        }
        // 服务启停是异步的，稍等再刷新磁贴状态
        new android.os.Handler(getMainLooper()).postDelayed(this::render, 500);
    }

    private void render() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = DnsControl.running();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("烫梨拦截");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_nav_shield));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) t.setSubtitle(on ? "已开启" : "已关闭");
        t.updateTile();
    }
}
