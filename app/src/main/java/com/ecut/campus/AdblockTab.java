package com.ecut.campus;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** 「拦截」页签（从 MainActivity 拆出）：护盾开关、暂停、自测、域名查询、状态文案。 */
final class AdblockTab {

    private final MainActivity a;
    private final View adblockFragment;
    private TextView statusText;
    private TextView statusSubtext;
    private TextView shieldBadge;
    private com.ecut.campus.view.ShieldRingView toggleBtn;
    private MaterialCardView adblockHeroCard;

    AdblockTab(MainActivity a, View root) {
        this.a = a;
        this.adblockFragment = root;
    }

    void bind() {
        adblockHeroCard = adblockFragment.findViewById(R.id.adblock_hero_card);
        statusText = adblockFragment.findViewById(R.id.status_text);
        statusSubtext = adblockFragment.findViewById(R.id.status_subtext);
        shieldBadge = adblockFragment.findViewById(R.id.tv_shield_badge);
        toggleBtn = adblockFragment.findViewById(R.id.btn_toggle);
        MaterialSwitch swObserve = adblockFragment.findViewById(R.id.switch_block_observe);

        TextView tvBlock = adblockFragment.findViewById(R.id.stat_block_count);
        TextView tvObserve = adblockFragment.findViewById(R.id.stat_observe_count);
        TextView tvAllow = adblockFragment.findViewById(R.id.stat_allow_count);
        // 10 万条规则解析要一两秒，绝不能在主线程等：先显示占位，算完再填
        for (TextView tv : new TextView[]{tvBlock, tvObserve, tvAllow}) {
            if (tv != null) tv.setText("—");
        }
        new Thread(() -> {
            DomainRules rules = DomainRules.get(a.getApplicationContext());
            int b = rules.blockCount(), o = rules.observeCount(), al = rules.allowCount();
            a.runOnUiThread(() -> {
                if (a.isFinishing()) return;
                if (tvBlock != null) tvBlock.setText(String.valueOf(b));
                if (tvObserve != null) tvObserve.setText(String.valueOf(o));
                if (tvAllow != null) tvAllow.setText(String.valueOf(al));
            });
        }, "rules-count").start();

        if (swObserve != null) {
            swObserve.setChecked(Prefs.blockObserve(a));
            swObserve.setOnCheckedChangeListener((btn, checked) -> {
                Prefs.setBlockObserve(a, checked);
                if (DnsFilterService.isRunning) {
                    Toast.makeText(a, "重启拦截后对统计域名生效", Toast.LENGTH_SHORT).show();
                }
            });
        }

        toggleBtn.setOnClickListener(v -> {
            DiagLog.log(a, "dns", DnsFilterService.isRunning ? "用户关闭拦截" : "用户开启拦截");
            if (DnsFilterService.isRunning) {
                DnsControl.stop(a);
                statusText.postDelayed(this::refreshStatus, 300);
            } else {
                prepareAndStartVpn();
            }
        });

        View pauseBtn = adblockFragment.findViewById(R.id.btn_dns_pause);
        if (pauseBtn != null) pauseBtn.setOnClickListener(v -> {
            if ((DnsControl.pausedUntil(a) > 0 || DnsControl.resumeOverdue(a)) && !DnsFilterService.isRunning) {
                // 正在暂停：这个按钮变成「现在恢复」
                prepareAndStartVpn();
                return;
            }
            if (!DnsFilterService.isRunning) {
                Toast.makeText(a, "护盾没开，不用暂停", Toast.LENGTH_SHORT).show();
                return;
            }
            Runnable doPause = () -> {
                DiagLog.log(a, "dns", "用户暂停拦截 30 分钟");
                DnsControl.pause(a, 30);
                statusText.postDelayed(this::refreshStatus, 300);
            };
            if (!DnsControl.canNotify(a)) {
                new androidx.appcompat.app.AlertDialog.Builder(a)
                        .setTitle("通知被关了")
                        .setMessage("30 分钟后烫梨会试着自动恢复；如果系统不让，要靠一条通知提醒你点一下。现在通知是关的，到点没恢复的话需要你回来手动打开。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("照样暂停", (d, w) -> doPause.run())
                        .show();
                return;
            }
            doPause.run();
        });

        TextView testResult = adblockFragment.findViewById(R.id.tv_dns_test_result);
        View testBtn = adblockFragment.findViewById(R.id.btn_dns_test);
        if (testBtn != null) testBtn.setOnClickListener(v -> {
            testBtn.setEnabled(false);
            if (testResult != null) {
                testResult.setVisibility(View.VISIBLE);
                testResult.setTextColor(G.SUB);
                testResult.setText("正在解析一个广告域名…");
            }
            new Thread(() -> {
                DnsControl.Verdict r = DnsControl.selfTest(a.getApplicationContext());
                a.runOnUiThread(() -> {
                    if (a.isFinishing()) return;
                    testBtn.setEnabled(true);
                    if (testResult == null) return;
                    testResult.setTextColor(r.good ? G.INK : G.alarm());
                    testResult.setText((r.good ? "✓ " : "✕ ") + r.title + " · " + r.detail);
                });
            }, "dns-selftest").start();
        });

        android.widget.EditText lookupInput = adblockFragment.findViewById(R.id.et_dns_lookup);
        View lookupBtn = adblockFragment.findViewById(R.id.btn_dns_lookup);
        TextView lookupTitle = adblockFragment.findViewById(R.id.tv_dns_lookup_title);
        TextView lookupDetail = adblockFragment.findViewById(R.id.tv_dns_lookup_detail);
        Runnable doLookup = () -> {
            if (lookupInput == null) return;
            String q = lookupInput.getText().toString();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(lookupInput.getWindowToken(), 0);
            new Thread(() -> {
                DnsControl.Verdict r = DnsControl.lookup(a.getApplicationContext(), q);
                a.runOnUiThread(() -> {
                    if (a.isFinishing() || lookupTitle == null || lookupDetail == null) return;
                    lookupTitle.setVisibility(View.VISIBLE);
                    lookupDetail.setVisibility(View.VISIBLE);
                    lookupTitle.setTextColor(r.good ? G.INK : G.SUB);
                    lookupTitle.setText((r.good ? "● " : "○ ") + r.title);
                    lookupDetail.setText(r.detail);
                });
            }, "dns-lookup").start();
        };
        if (lookupBtn != null) lookupBtn.setOnClickListener(v -> doLookup.run());
        if (lookupInput != null) lookupInput.setOnEditorActionListener((tv, id, ev) -> {
            doLookup.run();
            return true;
        });
    }

    /** 开着/暂停中每分钟刷一次文案。只在拦截页可见时跑。 */
    private final Runnable uptimeTick = new Runnable() {
        @Override
        public void run() {
            if (a.currentTab != MainActivity.TAB_ADBLOCK || statusText == null || a.isFinishing()) return;
            refreshStatus();
        }
    };

    void prepareAndStartVpn() {
        Intent prep = VpnService.prepare(a);
        if (prep != null) a.startActivityForResult(prep, MainActivity.REQ_VPN);
        else onVpnGranted();
    }

    /** VPN 授权通过（或本来就有）：启动拦截服务。 */
    void onVpnGranted() {
        startService(a);
        if (statusText != null) statusText.postDelayed(this::refreshStatus, 500);
    }

    static void startService(Context c) {
        Intent svc = new Intent(c, DnsFilterService.class)
                .setAction(DnsFilterService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(svc);
        else c.startService(svc);
        DnsControl.markStarted(c);
    }

    void onVpnDenied() {
        if (statusText != null) statusText.setText("VPN 授权被拒绝，无法开启拦截");
    }

    void refreshStatus() {
        if (statusText == null) return;
        statusText.removeCallbacks(uptimeTick);
        boolean on = DnsFilterService.isRunning;
        long pausedUntil = DnsControl.pausedUntil(a);
        boolean overdue = DnsControl.resumeOverdue(a);
        if (toggleBtn != null) toggleBtn.setOn(on);
        if (adblockHeroCard != null) adblockHeroCard.setCardBackgroundColor(on ? G.CARD : G.CHIP);
        if (on) {
            statusText.setText(R.string.adblock_on);
            if (statusSubtext != null) statusSubtext.setText(DnsControl.uptimeLabel(DnsControl.uptimeMs(a)));
            if (shieldBadge != null) {
                shieldBadge.setText("● ACTIVE");
                shieldBadge.setTextColor(G.GLASS ? G.INK : G.ACCENT);
            }
            statusText.postDelayed(uptimeTick, 60_000L);
        } else {
            statusText.setText(overdue ? "暂停已结束，护盾还没开" : pausedUntil > 0 ? "护盾暂停中" : a.getString(R.string.adblock_off));
            if (statusSubtext != null) {
                statusSubtext.setText(overdue ? "系统没让后台自动恢复，点一下护盾或「现在恢复」"
                        : pausedUntil > 0 ? DnsControl.pauseLabel(pausedUntil, System.currentTimeMillis())
                        : "点一下护盾开启本地 DNS 拦截");
            }
            if (shieldBadge != null) {
                shieldBadge.setText(overdue ? "! NOT RESUMED" : pausedUntil > 0 ? "Ⅱ PAUSED" : "○ STANDBY");
                shieldBadge.setTextColor(overdue ? G.alarm() : G.INK);
            }
            if (pausedUntil > 0) statusText.postDelayed(uptimeTick, 60_000L);
        }
        if (adblockFragment != null) {
            TextView pause = adblockFragment.findViewById(R.id.btn_dns_pause);
            if (pause != null) pause.setText((pausedUntil > 0 || overdue) && !on ? "现在恢复" : "暂停 30 分钟");
        }
    }
}
