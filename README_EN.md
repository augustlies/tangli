# Tangli

[简体中文](README.md) | **English**

Website & downloads: https://tangli.sueexin.dev/

An unofficial timetable tool for university students: **edit your timetable with one sentence, through any AI** — view your timetable, check your grades, find free time with classmates, and block annoying splash ads along the way.

Log in once to your school's academic system, and your timetable and grades are stored on your phone — they open instantly, even offline or off the campus network. Tangli never saves your account or password.

[**⬇ Download the latest release**](https://github.com/augustlies/tangli/releases/latest) · [Edit your timetable with AI](#-edit-your-timetable-with-ai) · [Supported schools](#supported-schools) · [Feedback](#feedback) · [Changelog](CHANGELOG.md)

---

## ✨ Edit your timetable with AI

Other timetable apps make you tap cell by cell. Tangli lets **any AI you already use** (Doubao, DeepSeek, Kimi, ChatGPT…) do it for you — no plugins, no extra accounts:

1. On the timetable page, tap "+" → "Edit timetable with AI". Tangli copies a briefing to your clipboard (it includes the current week number, your daily schedule, and your existing timetable).
2. Paste it to the AI and say what you want changed at the end, for example:
   - "Add a College Physics midterm exam next Wednesday, 2:30–4:30 PM, in A201"
   - "Move Advanced Math from Friday of week 10, periods 1–2, to Saturday of week 11, periods 3–4"
   - "National Day holiday runs Oct 1–7; make up the Oct 3 classes on Oct 11"
   - "Make every period 40 minutes, with the first period starting at 8:00"
   - Or just send a screenshot of your timetable / a notice from your teacher
3. Copy the "Tangli command" the AI replies with and paste it back — Tangli shows a preview listing every addition, deletion, and change. **Nothing takes effect until you confirm**, and mistakes can be undone.

The AI's reply looks roughly like this (the command syntax is in Chinese — it is human-readable, and you can also write it by hand):

```
烫梨指令 v1
+ 大学物理期中考试 | 周三 | 14:30-16:30 | 第10周 | A201
停 大学英语 | 第9周 | 周一 | 3-4节
调 高等数学 | 第10周 | 周五 | 1-2节 -> 第11周 | 周六 | 3-4节
假 2026-10-01 至 2026-10-07 | 国庆
```

- Sending the same class again with a new room or teacher updates it in place — no duplicates; specifying only some weeks changes only those weeks
- Add, delete, suspend, reschedule classes, holidays and make-up classes, term start dates, daily schedules, course notes, and common settings — all in one sentence
- Ask the AI for a `tangli://` link and send it to a classmate; one tap imports the same changes (confirmation still required)
- Commands are parsed on your phone only. Tangli never sends your timetable to any server; which AI you share it with, and how much, is up to you

---

## Supported schools

Go to Settings → School and **search your school's name directly** (built-in academic-system entry points for about 1,500 schools, none ranked above others), or enter your school's academic-system URL ("Custom school") and Tangli will recognize which system it is. Can't find the URL? Tap "Help me find it", search for the login page inside the app, and tap "Use this URL":

| Academic system | Timetable | Grades |
|---|---|---|
| Zhengfang （正方） | ✅ Background sync after one login | ✅ Background sync + new-grade alerts |
| Qiangzhi （强智） | ✅ Background sync after one login | ✅ Background sync + new-grade alerts |
| Qiangzhi Mobile (URL contains /sjd/) | 🟡 Log in on the web page, then tap "Import this page's timetable" | — |
| Jinzhi online service hall (e.g. Nanjing University) | 🟡 Open "My timetable", then tap "Import this page's timetable"; Nanjing University undergraduate / graduate entries built in | — |
| Other systems | 🟡 Open the timetable page in the academic portal, tap "Import this page's timetable", preview and confirm; if it can't be recognized, tap "Let AI read it" and any AI can read the whole timetable for you | — |

- Unrecognized systems show "not supported yet" — you can export a page-structure sample **without your name or student ID** in one tap and send it to me to get your school adapted
- Every school's academic pages differ slightly; if something doesn't work the first time, please [send feedback](#feedback) and just tell me the school name
- If sync really won't work, no problem: you can add classes manually, or let any AI organize them for you (see "Edit your timetable with AI" above)

## Features

### 📅 Timetable
- A whole week on one screen, opening on the current week; swipe to change weeks, or **press and drag the big week number in the top-left** — one cell, one week
- The class you are currently in "fills with time", showing how many minutes are left; 15 minutes before the next class, a countdown appears on its corner
- **Rain hint on class blocks** (on by default, can be turned off in Settings): when rain is expected during a class, its card on the timetable and the home-screen widget glows with a rainy-blue halo and drizzle streaks to remind you to take an umbrella; tap the class to see the rain probability. Weather is checked against Open-Meteo using a coarse location only (accurate to about 1 km)
- **"Today's classes" home-screen widget**: see today's remaining classes without opening the app, with live countdowns ("mm:ss left" for the current class, "starts in mm:ss" for the next); resizable down to 2×2, with your own background image. Long-press the Tangli icon and tap "Add today's classes widget" to place it
- **Pre-class reminders** (off by default): a silent notification 5–20 minutes ahead, with class name, room, and countdown
- **Next class pinned in the notification shade** (off by default): a silent notification with the next class's name, time, and room; during class it counts down to the end, and it flips automatically between classes
- Two classes at the same time are shown side by side, never overlapping
- **Long-press an empty cell and drag up or down** to draw a class directly; you can also add and edit classes manually
- 8–16 periods a day, period length, breaks, and 5- or 7-day display can all be adjusted to your school's schedule
- Same-name courses share a color; different courses in the same term never clash
- Holidays are greyed out automatically; make-up days automatically carry the classes of the day they replace; long-press a class to suspend just this week, or move it elsewhere
- You get a reminder if the timetable hasn't synced in a long time or the login has expired
- **Edit your timetable with AI**: see [above](#-edit-your-timetable-with-ai)

### 🎓 Grades
- View grades and GPA by term
- A notification when new grades are posted (checked about every 6 hours, can be turned off)
- New grades stay "covered" until you tap to reveal them; you can read a one-line comment before revealing the score

### 👫 Classmates
- Generate a QR code or share code to send your timetable to a classmate (only course names, rooms, times, and schedule — no teachers or student IDs); if their daily schedule differs, they can adopt yours in one tap
- Scan a code or paste one to import a classmate's timetable
- Compute the **common free time** of you and one or more classmates in one tap — no more comparing timetables class by class to plan a meal or a study session

### 🎲 Little extras
- Settings → Little extras: how much each class is worth in tuition, a red edge on the timetable when absences approach 1/3, a two-person timetable poster, and a term persona card — all computed on your phone

### 🛡 Ad filtering
- Filters ad domains locally on your phone; splash and pop-up ads in campus apps are blocked
- Can be paused for 30 minutes (resumes automatically), with a one-tap test and a lookup for whether a given URL would be blocked; a quick-settings tile toggles it too
- Rules combine the author's own list with the open-source [anti-AD](https://github.com/privacy-protection-tools/anti-AD) project — about 100,000 rules in total
- Only domain lookups are handled; nothing goes through any server, and your other network traffic is untouched

### 🎨 Appearance & more
- New "Gauge Pear" icon; Settings → General → Appearance → "App icon" can switch back to the previous "Big Fat Fish"
- Two themes, "Paper & Ink" and "Frosted Glass", each in light / dark, expanding in a circle from where you tap when switching; dialogs under Frosted Glass are nearly opaque, so text never blends into the background
- Export your timetable to your phone's calendar, or back it up to a file to restore on a new phone
- New versions can be downloaded and installed in-app (signature verified before installing); Settings → About can check for updates and jump to this project
- Some adapted schools also have shortcuts such as the academic calendar and campus map

## Installation

1. Open the [Releases page](https://github.com/augustlies/tangli/releases/latest) and download the latest `tangli-x.x.x.apk`
2. Open it on your phone to install; when the system warns about an "unknown source", allow it once
3. Open Tangli, pick your school in Settings → School, then log in to the academic system to sync once
4. Tangli will remind you in the app when new versions are out

> Requires Android 7.0 or above. When you turn on ad filtering, the system will say Tangli wants to set up a VPN — this is how local filtering works on Android; your data never leaves your phone.

## Feedback

Problems, a school that won't sync, or a feature you'd like? Tell me:

- 📧 Email: **contact@sueexin.dev**
- 🐛 Or open an [Issue](https://github.com/augustlies/tangli/issues/new) on GitHub

When reporting, please say which school, which page, what you did, and what you saw — a screenshot helps a lot. If the app hits an error, the dialog's "Send to developer" button sends me the error log in one tap (it contains no student ID, name, account password, or cookies) — just note the report code it gives you; Settings → About also has "Send error log in one tap". Email feedback automatically includes the version number, device model, and a diagnostic log.

## Notes

- Tangli is an unofficial tool made by a student, and is not affiliated with any school or academic-system vendor
- Timetables, grades, and other data from your school's academic system are authoritative; GPA calculations are for reference only
- Your account and password are never saved, and timetables and grades live only on your own phone. If the rain hint is on and location is allowed, a location accurate to about 1 km is sent to Open-Meteo for weather (can be turned off in Settings). An error log with personal information removed is uploaded only when you actively tap "Send to developer"

## License

[GPL-3.0](LICENSE), © 2026 August Lies. Versions v1.2.3 and earlier were released under MIT. The ad-blocking rules partly come from [anti-AD](https://github.com/privacy-protection-tools/anti-AD) (MIT); the Frosted Glass theme's design references the galaxy component on [Uiverse.io](https://uiverse.io) (MIT) — see [NOTICE](NOTICE) for details.

Want to contribute? See [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/](docs/).
