# TramAgent — Android FUTÁR / GTFS demonstrációs alkalmazás

TramAgent egy Android-alkalmazás, amely a BKK FUTÁR / BKK GO API és a GTFS adatok felhasználásával valós idejű jármű- és menetrendi információkat jelenít meg.
A projekt célja egy többmodulos, jól átlátható demonstráció létrehozása, amely bemutatja a tömegközlekedési adatok feldolgozását Android-on.

##  Fő funkciók

A TramAgent több különálló demómodult tartalmaz:

### **FORGALMISZÁM KERESŐ (DK1) – Járművek megjelenítése adott viszonylaton**
- FUTÁR `vehicles-for-route` végpont használata  
- Forgalmi szám + rendszám kinyerése  
- Viszonylaton közlekedő összes jármű listázása

### **AKTÍV ZAVAROK (DK2) – Valós idejű zavarok (GTFS-RT Alerts)**
- GTFS-RT Alerts (`alerts.pb`) letöltése és feldolgozása  
- Csak villamosokra szűrt zavarok megjelenítése  
- Szolgáltatás-kiesés, késés, terelés, stb.

### **INDULÁSOK A KÖZELEMBEN (DK3) – Közeli megállók és indulások**
- GPS alapú helymeghatározás  
- 300 méteren belüli megállók keresése (GTFS `stops.txt`)  
- Soron következő indulások szűrése 60 percen belül  
- Villamosokra szűrés (`route_type = 0 vagy 900`)

### **TÉRKÉPES KERESŐ (DK4) – Jármű pozíció térképen**
- Google Maps integráció  
- Valós idejű jármű marker (villamos ikon)  
- Trip headsign (irány) megjelenítése  
- A trip összes megállójának kirajzolása GTFS adatokkal  
- Predicted / Scheduled időpontok kezelése

### **NYOMKÖVETÉS (DK5)– Részletes menetrend + késés számítása**
- TripId alapján FUTÁR `trip-details` használata  
- API-beli késés értelmezése (`arrivalDelay`)  
- Színkódolt késés kijelzés:
  - **piros** — nagy előfutás  
  - **zöld** — kisebb késés / normál tartomány  
  - **kék** — nagy késés  

### **GTFS Frissítő modul**
- Teljes budapesti GTFS ZIP letöltése  
  https://go.bkk.hu/api/static/v1/public-gtfs/budapest_gtfs.zip  
- ZIP kicsomagolása Android belső tárhelyre  
- Könyvtárstruktúra:

```
files/gtfs/
    stops.txt
    trips.txt
    stop_times.txt
    routes.txt
    ...
```

---

##  Projekt felépítése

```
app/
 ├─ data/         → BkkApi (FUTÁR/GO REST lekérdezések + GTFS loader)
 ├─ domain/       → DK1–DK5 logikák
 ├─ ui/           → Activity-k (DK1–DK5)
 ├─ gtfs/         → GtfsUpdater (ZIP letöltés + kicsomagolás)
 ├─ res/layout/   → Képernyő elrendezések
 └─ ...
```

Az architektúra szétválasztja:
- **UI réteg** (Android felület)  
- **Domain** (logika)  
- **Data** (API + fájl hozzáférés)

---

##  API-kulcsok

### Google Maps API kulcs
A projekt nem tartalmaz éles kulcsot.  
Futtatáshoz add meg a saját kulcsodat:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY_HERE" />
```

### BKK FUTÁR API kulcs

`BkkApi.kt`:

```kotlin
const val API_KEY = "YOUR_BKK_API_KEY_HERE"
```

 A repóban csak helykitöltők szerepelnek.  
 A saját kulcsodat **ne commitold**.

---

##  Futtatás

1. Klónozd a repót:
   ```bash
   git clone https://github.com/AmerThamer/tram-agent.git
   ```
2. Nyisd meg Android Studio-ban  
3. Add meg a saját BKK + Google Maps API kulcsokat  
4. Futtasd a GTFS Frissítőt  
5. Teszteld a DK1–DK5 modulokat  



Licenc

MIT License
