# NotchPixel Island (Android) - Final (step 31)

Screen ke top-center par black "island" pill. Koi external library nahi, INTERNET permission nahi.

## Kya kya hai
- **Idle pill:** time + battery dot. Charging = hara dot + bijli, battery 15% se kam = laal dot, torch on = peela dot.
- **Tap:** panel khulta hai, 3 tabs: Websites, Controls, Apps (aakhri tab yaad rehta hai).
  Site / app tile par long-press = manage screen.
- **Pill par hold:** 2 second ke andar right slide = Quick panel (sab settings aur sliders). Slide na karo to bada tree khulta hai: Websites, Controls, Apps (baqi gol khali). Tabs sirf tree se kholte hain.
- **Calls (WhatsApp, Instagram, Skype, Google Voice...):**
  1. Aati call: pill apne aap khulta hai, app ka rang + naam, seedha Answer (hara) / Decline (laal).
  2. Chalti call: chhota pill, badge + hilti waveform + timer. Tap = bada card.
  3. Bada card: naam, bada timer, waveform, "Call kholo" / "Hang up". 10 second ya bahar tap par wapas pill.
  4. Call khatam: "Call ended 05:42", 2 second, phir idle pill.

## APK banane ke 2 tareeqe
A) Android Studio: File > Open > yeh folder > Run (USB debugging ON) ya Build > Build APK.
B) GitHub (bina Studio): naya repo banao, yeh poora folder upload/push karo >
   Actions tab > "Build APK" > artifact "NotchPixelIsland-apk" download > phone mein install.

## Phone par setup (ek baar)
1. APK install (Unknown sources allow).
2. App kholo > **Allow "Display over other apps"** > **Start Island**.
3. (Optional) **Allow brightness slider** (Modify system settings).
4. (Optional) **Allow call detection** (Notification access) - calls ke island ke liye zaroori.
5. Xiaomi / Redmi / POCO (MIUI / HyperOS) ho to app mein **"6. MIUI / battery guide"** kholo.
   Wahan 6 step hain, har ek ka "Kholo" button seedha sahi screen kholta hai.

## Call island ko test karna
Island start karo, phir app mein **"5. Test: nakli aati call"** dabao. Answer dabao to chalti call ka pill,
pill par tap = bada card, Hang up = "Call ended". Asli call mein bhi yehi flow hai.

## MIUI par pill ghayab ho jaye to
- Autostart ON, Battery saver = "No restrictions", "Display pop-up windows while running in the background" ON.
- Recents mein app ko lock karo.
- Notification access band ho gayi ho to dobara ON karo (MIUI kabhi kabhi use hata deta hai).

## Battery
- Koi polling nahi: battery / time / torch ki khabar system se aati hai.
- Idle mein sirf 15 second par time update, aur screen band hote hi woh bhi ruk jata hai.
- Call ki waveform aur pulse sirf call dikhte waqt chalte hain (~15 fps) aur screen band hote hi ruk jate hain.
- Animation sirf khulte / band hote waqt chalti hai.

## Sachchi baatein (limits)
- **Waveform asli awaaz nahi hai**, sirf hilti hui bars. Android doosri app ki call ki awaaz pakadne nahi deta.
- Timer notification ke chronometer se aata hai, na mile to island pehli baar dekhne ke waqt se ginta hai.
- Answer / Decline / Hang up us app ki notification ke apne buttons ko dabate hain. Kisi app ki notification
  mein woh button na ho to tap par woh app khul jati hai.
- Wi-Fi, Bluetooth, Airplane, Mobile data, Location: Android 10+ par seedha on/off allowed nahi, system screen khulti hai.
- **Screenshot aur Lock tiles abhi kaam nahi karte** (blueprint ka optional Phase 3, Accessibility service chahiye).

- **Tree:** hold karke tree khulne ke baad ungli uthaye bina kisi button par ruko (ring bharti hai) to wo khulta hai. Ungli uthao to island normal.
- **Edge sliders:** screen ke right kinare par upar/neeche = volume, left par = brightness (MainActivity mein ON/OFF).

- **Tree:** hold karke tree khulne ke baad ungli uthaye bina kisi button par ruko (ring bharti hai) to wo khulta hai. Ungli uthao to island normal.
- **Edge sliders:** screen ke right kinare par upar/neeche = volume, left par = brightness (MainActivity mein ON/OFF).
- **Logo:** res/mipmap-anydpi-v26 (adaptive icon).

## Step 31 changes
- Quick panel, controls row aur brightness slider ke emoji hata kar vector icons (`res/drawable/ic_q*.xml`).
- Edge slide: right = volume, left = brightness, level island ke andar icon + bar + number.
